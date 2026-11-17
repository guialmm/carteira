package io.github.guialmm.carteira.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One side of a transfer. Entries are append-only: a transfer writes exactly two, summing to zero. */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "balance_after_cents", nullable = false)
    private long balanceAfterCents;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    LedgerEntry(UUID transferId, UUID accountId, long amountCents, long balanceAfterCents, Instant createdAt) {
        this.transferId = transferId;
        this.accountId = accountId;
        this.amountCents = amountCents;
        this.balanceAfterCents = balanceAfterCents;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public long getBalanceAfterCents() {
        return balanceAfterCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
