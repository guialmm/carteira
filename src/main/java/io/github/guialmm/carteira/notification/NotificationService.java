package io.github.guialmm.carteira.notification;

import io.github.guialmm.carteira.events.TransferCompleted;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class NotificationService {

    static final String CONSUMER = "notifications";

    record Notification(UUID id, String kind, String message, long amountCents, UUID transferId,
            java.time.Instant createdAt) {
    }

    private final JdbcClient jdbc;

    NotificationService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Idempotent: the event id is recorded in the same transaction as the notifications, so a
     * redelivered message (at-least-once) is recognised and skipped. Returns false for duplicates.
     */
    @Transactional
    boolean handle(TransferCompleted e) {
        int fresh = jdbc.sql("""
                insert into processed_events (consumer, event_id) values (:consumer, :id)
                on conflict do nothing
                """)
                .param("consumer", CONSUMER)
                .param("id", e.eventId())
                .update();
        if (fresh == 0) {
            return false;
        }
        String amount = Money.brl(e.amountCents());
        if ("DEPOSIT".equals(e.kind())) {
            insert(e.destinationAccountId(), e, "DEPOSIT", "Depósito de " + amount + " recebido");
        } else {
            insert(e.destinationAccountId(), e, "RECEIVED", "Você recebeu " + amount + " de " + e.sourceName());
            insert(e.sourceAccountId(), e, "SENT", "Você enviou " + amount + " para " + e.destinationName());
        }
        return true;
    }

    @Transactional(readOnly = true)
    List<Notification> latest(UUID accountId, int limit) {
        return jdbc.sql("""
                select id, kind, message, amount_cents, transfer_id, created_at from notifications
                where account_id = :account order by created_at desc, id limit :limit
                """)
                .param("account", accountId)
                .param("limit", limit)
                .query((rs, i) -> new Notification(rs.getObject("id", UUID.class), rs.getString("kind"),
                        rs.getString("message"), rs.getLong("amount_cents"), rs.getObject("transfer_id", UUID.class),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    private void insert(UUID account, TransferCompleted e, String kind, String message) {
        jdbc.sql("""
                insert into notifications (id, account_id, transfer_id, kind, message, amount_cents, created_at)
                values (:id, :account, :transfer, :kind, :message, :amount, :at)
                """)
                .param("id", UUID.randomUUID())
                .param("account", account)
                .param("transfer", e.transferId())
                .param("kind", kind)
                .param("message", message)
                .param("amount", e.amountCents())
                .param("at", java.sql.Timestamp.from(e.occurredAt()))
                .update();
    }
}
