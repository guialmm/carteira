package io.github.guialmm.carteira.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import io.github.guialmm.carteira.TestcontainersConfiguration;
import io.github.guialmm.carteira.account.AccountService;
import io.github.guialmm.carteira.transfer.TransferService;
import io.github.guialmm.carteira.user.User;
import io.github.guialmm.carteira.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The broker is replaced by a mock that fails, then recovers. Money must move regardless. */
@SpringBootTest(properties = "carteira.outbox.publisher.interval=1h")
@Import(TestcontainersConfiguration.class)
class OutboxBrokerDownTest {

    @MockitoBean RabbitTemplate rabbit;
    @Autowired OutboxPublisher publisher;
    @Autowired TransferService transfers;
    @Autowired AccountService accounts;
    @Autowired UserRepository users;
    @Autowired OutboxRepository outbox;
    @Autowired JdbcClient jdbc;

    @Test
    void transfersStillCommitAndEventsWaitUntilTheBrokerIsBack() {
        when(rabbit.invoke(any())).thenThrow(new AmqpConnectException(new java.net.ConnectException("broker down")));
        UUID ana = open("Ana");
        UUID bruno = open("Bruno");
        transfers.deposit(ana, 1_000, UUID.randomUUID().toString());
        UUID transferId = transfers.transfer(ana, bruno, 300, null, UUID.randomUUID().toString()).transfer().getId();

        assertThat(publisher.publishBatch()).isZero();
        var pending = outbox.findByAggregateId(transferId).getFirst();
        assertThat(pending.getPublishedAt()).isNull();
        assertThat(jdbc.sql("select count(*) from outbox_events where published_at is null and last_error is not null")
                .query(Long.class).single()).isPositive();

        reset(rabbit); // broker is back: invoke() now succeeds
        while (publisher.publishBatch() > 0) {
            // drain everything that piled up
        }

        assertThat(outbox.findById(pending.getId()).orElseThrow().getPublishedAt()).isNotNull();
        assertThat(jdbc.sql("select count(*) from outbox_events where published_at is null").query(Long.class).single())
                .isZero();
    }

    private UUID open(String name) {
        var user = users.save(new User("u-" + UUID.randomUUID() + "@test.dev", name, "x"));
        return accounts.openCustomerAccount(user.getId()).getId();
    }
}
