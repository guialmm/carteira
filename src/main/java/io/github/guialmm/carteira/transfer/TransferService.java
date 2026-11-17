package io.github.guialmm.carteira.transfer;

import static io.github.guialmm.carteira.transfer.TransferException.Reason.ACCOUNT_NOT_FOUND;
import static io.github.guialmm.carteira.transfer.TransferException.Reason.IDEMPOTENCY_KEY_REUSED;
import static io.github.guialmm.carteira.transfer.TransferException.Reason.INSUFFICIENT_FUNDS;
import static io.github.guialmm.carteira.transfer.TransferException.Reason.INVALID_AMOUNT;
import static io.github.guialmm.carteira.transfer.TransferException.Reason.SAME_ACCOUNT;

import io.github.guialmm.carteira.account.Account;
import io.github.guialmm.carteira.account.AccountKind;
import io.github.guialmm.carteira.account.AccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves money between accounts.
 *
 * <p>Concurrency: both account rows are locked with {@code SELECT ... FOR UPDATE}, always in
 * ascending id order, so two opposite transfers (A→B and B→A) can never deadlock. The balance
 * check happens after the locks, so it reads the latest committed balance.
 *
 * <p>Idempotency: the key is looked up only after the locks are held. Two requests with the same
 * key both lock the initiator's account, so the second one waits and then finds the first one's
 * transfer instead of creating another. The unique constraint on (initiator, key) is the backstop.
 */
@Service
public class TransferService {

    public static final long MAX_DEPOSIT_CENTS = 100_000; // R$ 1.000,00 per demo deposit

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final LedgerEntryRepository entries;

    public TransferService(AccountRepository accounts, TransferRepository transfers, LedgerEntryRepository entries) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.entries = entries;
    }

    @Transactional
    public TransferResult transfer(UUID sourceId, UUID destinationId, long amountCents,
            String description, String idempotencyKey) {
        return move(TransferKind.TRANSFER, sourceId, destinationId, sourceId, amountCents, description, idempotencyKey);
    }

    @Transactional
    public TransferResult deposit(UUID accountId, long amountCents, String idempotencyKey) {
        if (amountCents > MAX_DEPOSIT_CENTS) {
            throw new TransferException(INVALID_AMOUNT, "Deposits are limited to %d cents".formatted(MAX_DEPOSIT_CENTS));
        }
        return move(TransferKind.DEPOSIT, Account.FUNDING_ID, accountId, accountId, amountCents, "Depósito", idempotencyKey);
    }

    private TransferResult move(TransferKind kind, UUID sourceId, UUID destinationId, UUID initiatorId,
            long amountCents, String description, String idempotencyKey) {
        if (amountCents <= 0) {
            throw new TransferException(INVALID_AMOUNT, "Amount must be positive");
        }
        if (sourceId.equals(destinationId)) {
            throw new TransferException(SAME_ACCOUNT, "Source and destination are the same account");
        }

        // Lock in a global order (by id) so concurrent opposite transfers cannot deadlock.
        boolean sourceFirst = sourceId.compareTo(destinationId) < 0;
        Account first = lock(sourceFirst ? sourceId : destinationId);
        Account second = lock(sourceFirst ? destinationId : sourceId);
        Account source = sourceFirst ? first : second;
        Account destination = sourceFirst ? second : first;

        // Only customer accounts can receive a transfer; the funding account is internal.
        if (kind == TransferKind.TRANSFER && destination.getKind() != AccountKind.CUSTOMER) {
            throw new TransferException(ACCOUNT_NOT_FOUND, "Account not found: " + destinationId);
        }

        String requestHash = hash(kind, sourceId, destinationId, amountCents, description);
        var previous = transfers.findByInitiatorAccountIdAndIdempotencyKey(initiatorId, idempotencyKey);
        if (previous.isPresent()) {
            if (!previous.get().getRequestHash().equals(requestHash)) {
                throw new TransferException(IDEMPOTENCY_KEY_REUSED,
                        "This idempotency key was already used for a different request");
            }
            return new TransferResult(previous.get(), true);
        }

        if (!source.canDebit(amountCents)) {
            throw new TransferException(INSUFFICIENT_FUNDS, "Insufficient funds");
        }

        var transfer = transfers.save(new Transfer(kind, sourceId, destinationId, initiatorId, amountCents,
                description, idempotencyKey, requestHash));
        long sourceBalance = source.apply(-amountCents);
        long destinationBalance = destination.apply(amountCents);
        entries.save(new LedgerEntry(transfer.getId(), sourceId, -amountCents, sourceBalance, transfer.getCreatedAt()));
        entries.save(new LedgerEntry(transfer.getId(), destinationId, amountCents, destinationBalance, transfer.getCreatedAt()));
        return new TransferResult(transfer, false);
    }

    private Account lock(UUID id) {
        return accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new TransferException(ACCOUNT_NOT_FOUND, "Account not found: " + id));
    }

    private static String hash(TransferKind kind, UUID source, UUID destination, long amount, String description) {
        String canonical = String.join("|", kind.name(), source.toString(), destination.toString(),
                Long.toString(amount), Objects.toString(description, ""));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
