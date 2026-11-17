package io.github.guialmm.carteira.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferKind kind;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", nullable = false)
    private UUID destinationAccountId;

    @Column(name = "initiator_account_id", nullable = false)
    private UUID initiatorAccountId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    private String description;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Transfer() {
    }

    Transfer(TransferKind kind, UUID source, UUID destination, UUID initiator, long amountCents,
            String description, String idempotencyKey, String requestHash) {
        this.id = UUID.randomUUID();
        this.kind = kind;
        this.sourceAccountId = source;
        this.destinationAccountId = destination;
        this.initiatorAccountId = initiator;
        this.amountCents = amountCents;
        this.description = description;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public TransferKind getKind() {
        return kind;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public UUID getInitiatorAccountId() {
        return initiatorAccountId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getDescription() {
        return description;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
