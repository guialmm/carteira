package io.github.guialmm.carteira.transfer;

import static io.github.guialmm.carteira.transfer.TransferException.Reason.IDEMPOTENCY_KEY_REUSED;
import static io.github.guialmm.carteira.transfer.TransferException.Reason.INSUFFICIENT_FUNDS;
import static io.github.guialmm.carteira.transfer.TransferException.Reason.SAME_ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guialmm.carteira.TestcontainersConfiguration;
import io.github.guialmm.carteira.account.Account;
import io.github.guialmm.carteira.account.AccountRepository;
import io.github.guialmm.carteira.account.AccountService;
import io.github.guialmm.carteira.user.User;
import io.github.guialmm.carteira.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferServiceTest {

    @Autowired TransferService service;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accounts;
    @Autowired UserRepository users;
    @Autowired LedgerEntryRepository entries;
    @Autowired JdbcClient jdbc;

    @Test
    void transferMovesMoneyAndWritesTwoEntriesThatSumToZero() {
        UUID alice = openAccount(10_000);
        UUID bob = openAccount(0);

        var result = service.transfer(alice, bob, 2_500, "almoço", key());

        assertThat(result.replayed()).isFalse();
        assertThat(balance(alice)).isEqualTo(7_500);
        assertThat(balance(bob)).isEqualTo(2_500);
        assertThat(jdbc.sql("select sum(amount_cents) from ledger_entries where transfer_id = ?")
                .param(result.transfer().getId()).query(Long.class).single()).isZero();
        assertLedgerMatchesBalance(alice, bob);
    }

    @Test
    void refusesWhenFundsAreInsufficientAndWritesNothing() {
        UUID alice = openAccount(1_000);
        UUID bob = openAccount(0);

        assertThatThrownBy(() -> service.transfer(alice, bob, 1_001, null, key()))
                .isInstanceOfSatisfying(TransferException.class, e -> assertThat(e.getReason()).isEqualTo(INSUFFICIENT_FUNDS));

        assertThat(balance(alice)).isEqualTo(1_000);
        assertThat(balance(bob)).isZero();
        assertLedgerMatchesBalance(alice, bob);
    }

    @Test
    void refusesTransferToTheSameAccount() {
        UUID alice = openAccount(1_000);

        assertThatThrownBy(() -> service.transfer(alice, alice, 100, null, key()))
                .isInstanceOfSatisfying(TransferException.class, e -> assertThat(e.getReason()).isEqualTo(SAME_ACCOUNT));
    }

    @Test
    void sameKeyAndSameRequestReplaysInsteadOfPayingTwice() {
        UUID alice = openAccount(10_000);
        UUID bob = openAccount(0);
        String key = key();

        var first = service.transfer(alice, bob, 1_000, "aluguel", key);
        var retry = service.transfer(alice, bob, 1_000, "aluguel", key);

        assertThat(retry.replayed()).isTrue();
        assertThat(retry.transfer().getId()).isEqualTo(first.transfer().getId());
        assertThat(balance(alice)).isEqualTo(9_000);
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() {
        UUID alice = openAccount(10_000);
        UUID bob = openAccount(0);
        String key = key();
        service.transfer(alice, bob, 1_000, null, key);

        assertThatThrownBy(() -> service.transfer(alice, bob, 2_000, null, key))
                .isInstanceOfSatisfying(TransferException.class,
                        e -> assertThat(e.getReason()).isEqualTo(IDEMPOTENCY_KEY_REUSED));
        assertThat(balance(alice)).isEqualTo(9_000);
    }

    @Test
    void twoHundredConcurrentTransfersNeverOverdraw() throws Exception {
        UUID alice = openAccount(10_000); // R$ 100,00
        UUID bob = openAccount(0);

        // 200 transfers of R$ 1,00 race for R$ 100,00: exactly 100 must win.
        var outcomes = runConcurrently(200, i -> () -> service.transfer(alice, bob, 100, null, key()));

        assertThat(outcomes.stream().filter(o -> o == null).count()).isEqualTo(100);
        assertThat(outcomes.stream().filter(o -> o == INSUFFICIENT_FUNDS).count()).isEqualTo(100);
        assertThat(balance(alice)).isZero();
        assertThat(balance(bob)).isEqualTo(10_000);
        assertLedgerMatchesBalance(alice, bob);
    }

    @Test
    void oppositeConcurrentTransfersDoNotDeadlock() throws Exception {
        UUID alice = openAccount(10_000);
        UUID bob = openAccount(10_000);

        var outcomes = runConcurrently(200, i -> () -> i % 2 == 0
                ? service.transfer(alice, bob, 100, null, key())
                : service.transfer(bob, alice, 100, null, key()));

        assertThat(outcomes).containsOnlyNulls();
        assertThat(balance(alice)).isEqualTo(10_000);
        assertThat(balance(bob)).isEqualTo(10_000);
        assertLedgerMatchesBalance(alice, bob);
    }

    @Test
    void concurrentRetriesWithTheSameKeyCreateOneTransfer() throws Exception {
        UUID alice = openAccount(10_000);
        UUID bob = openAccount(0);
        String key = key();

        var outcomes = runConcurrently(50, i -> () -> service.transfer(alice, bob, 500, "pix", key));

        assertThat(outcomes).containsOnlyNulls();
        assertThat(jdbc.sql("select count(*) from transfers where initiator_account_id = ? and idempotency_key = ?")
                .params(alice, key).query(Long.class).single()).isEqualTo(1);
        assertThat(balance(alice)).isEqualTo(9_500);
    }

    @Test
    void everyBalanceAddsUpToZeroAcrossTheWholeLedger() {
        UUID alice = openAccount(5_000);
        UUID bob = openAccount(0);
        service.transfer(alice, bob, 1_234, null, key());

        assertThat(jdbc.sql("select sum(balance_cents) from accounts").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("""
                select count(*) from accounts a
                where a.balance_cents <> (select coalesce(sum(e.amount_cents), 0) from ledger_entries e where e.account_id = a.id)
                """).query(Long.class).single()).isZero();
    }

    private UUID openAccount(long initialCents) {
        var user = users.save(new User("u-" + UUID.randomUUID() + "@test.dev", "Teste", "x"));
        UUID id = accountService.openCustomerAccount(user.getId()).getId();
        if (initialCents > 0) {
            service.deposit(id, initialCents, key());
        }
        return id;
    }

    private long balance(UUID id) {
        return accounts.findById(id).map(Account::getBalanceCents).orElseThrow();
    }

    private void assertLedgerMatchesBalance(UUID... ids) {
        for (UUID id : ids) {
            assertThat(entries.sumByAccountId(id)).as("ledger sum of %s", id).isEqualTo(balance(id));
        }
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    interface Task {
        Callable<TransferResult> make(int i);
    }

    /** Starts every task at the same instant; returns null for success or the refusal reason. */
    private static List<TransferException.Reason> runConcurrently(int n, Task task) throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<TransferResult>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Callable<TransferResult> call = task.make(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<TransferException.Reason> outcomes = new ArrayList<>();
            for (var f : futures) {
                try {
                    f.get();
                    outcomes.add(null);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof TransferException te) {
                        outcomes.add(te.getReason());
                    } else {
                        throw e;
                    }
                }
            }
            return outcomes;
        }
    }
}
