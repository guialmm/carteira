package io.github.guialmm.carteira.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import io.github.guialmm.carteira.TestcontainersConfiguration;
import io.github.guialmm.carteira.account.AccountService;
import io.github.guialmm.carteira.events.TransferCompleted;
import io.github.guialmm.carteira.transfer.TransferException;
import io.github.guialmm.carteira.transfer.TransferService;
import io.github.guialmm.carteira.user.User;
import io.github.guialmm.carteira.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxWriteTest {

    @Autowired TransferService transfers;
    @Autowired AccountService accounts;
    @Autowired UserRepository users;
    @Autowired OutboxRepository outbox;
    @Autowired OutboxWriter writer;
    @Autowired JdbcClient jdbc;

    @Test
    void aCommittedTransferLeavesExactlyOneEventWithEverythingConsumersNeed() {
        UUID ana = open("Ana", 5_000);
        UUID bruno = open("Bruno", 0);

        var t = transfers.transfer(ana, bruno, 1_990, "cinema", UUID.randomUUID().toString()).transfer();

        var events = outbox.findByAggregateId(t.getId());
        assertThat(events).hasSize(1);
        var e = events.getFirst();
        assertThat(e.getType()).isEqualTo(TransferCompleted.TYPE);
        assertThat(e.getPublishedAt()).isNull();
        String json = e.getPayload();
        assertThat((String) JsonPath.read(json, "$.sourceName")).isEqualTo("Ana");
        assertThat((String) JsonPath.read(json, "$.destinationName")).isEqualTo("Bruno");
        assertThat((Integer) JsonPath.read(json, "$.amountCents")).isEqualTo(1_990);
        assertThat((String) JsonPath.read(json, "$.eventId")).isEqualTo(e.getId().toString());
    }

    @Test
    void aReplayedRequestDoesNotEmitASecondEvent() {
        UUID ana = open("Ana", 5_000);
        UUID bruno = open("Bruno", 0);
        String key = UUID.randomUUID().toString();

        var t = transfers.transfer(ana, bruno, 100, null, key).transfer();
        transfers.transfer(ana, bruno, 100, null, key);

        assertThat(outbox.findByAggregateId(t.getId())).hasSize(1);
    }

    @Test
    void aRefusedTransferLeavesNoEvent() {
        UUID ana = open("Ana", 100);
        UUID bruno = open("Bruno", 0);
        long before = countEvents();

        assertThatThrownBy(() -> transfers.transfer(ana, bruno, 101, null, UUID.randomUUID().toString()))
                .isInstanceOf(TransferException.class);

        assertThat(countEvents()).isEqualTo(before);
    }

    @Test
    void appendingOutsideATransactionIsRefused() {
        assertThatThrownBy(() -> writer.append(UUID.randomUUID(), "x", UUID.randomUUID(), "{}"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private long countEvents() {
        return jdbc.sql("select count(*) from outbox_events").query(Long.class).single();
    }

    private UUID open(String name, long cents) {
        var user = users.save(new User("u-" + UUID.randomUUID() + "@test.dev", name, "x"));
        UUID id = accounts.openCustomerAccount(user.getId()).getId();
        if (cents > 0) {
            transfers.deposit(id, cents, UUID.randomUUID().toString());
        }
        return id;
    }
}
