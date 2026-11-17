package io.github.guialmm.carteira.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    public static final UUID FUNDING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountKind kind;

    @Column(name = "balance_cents", nullable = false)
    private long balanceCents;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
    }

    public static Account customer(UUID ownerId) {
        var account = new Account();
        account.id = UUID.randomUUID();
        account.ownerId = ownerId;
        account.kind = AccountKind.CUSTOMER;
        return account;
    }

    /** Applies a signed amount and returns the new balance. Callers must hold the row lock. */
    public long apply(long amountCents) {
        balanceCents = Math.addExact(balanceCents, amountCents);
        return balanceCents;
    }

    public boolean canDebit(long amountCents) {
        return kind == AccountKind.FUNDING || balanceCents >= amountCents;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public AccountKind getKind() {
        return kind;
    }

    public long getBalanceCents() {
        return balanceCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
