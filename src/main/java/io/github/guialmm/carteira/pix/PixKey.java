package io.github.guialmm.carteira.pix;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pix_keys")
public class PixKey {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PixKeyType type;

    @Column(nullable = false)
    private String value;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PixKey() {
    }

    PixKey(UUID accountId, PixKeyType type, String value) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.type = type;
        this.value = value;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public PixKeyType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
