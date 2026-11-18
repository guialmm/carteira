package io.github.guialmm.carteira.outbox;

import io.github.guialmm.carteira.events.EventsTopology;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Relays outbox rows to RabbitMQ.
 *
 * <p>Rows are claimed with {@code FOR UPDATE SKIP LOCKED}, so several API instances can run this
 * at once without sending the same event twice in parallel. A row is marked published only after
 * the broker confirms it (one confirm per batch). If the app dies between the confirm and the update, the event goes out
 * again on the next run: delivery is at-least-once, and consumers dedupe by message id.
 */
@Component
@ConditionalOnProperty(name = "carteira.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    static final int BATCH = 200;

    private record Pending(UUID id, String type, String payload) {
    }

    private final JdbcClient jdbc;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate tx;

    OutboxPublisher(JdbcClient jdbc, RabbitTemplate rabbit, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${carteira.outbox.publisher.interval:500ms}")
    void drain() {
        int sent;
        do {
            sent = publishBatch();
        } while (sent == BATCH);
    }

    /** Returns how many events were confirmed by the broker. */
    int publishBatch() {
        Integer sent = tx.execute(status -> {
            List<Pending> batch = jdbc.sql("""
                    select id, type, payload::text as payload from outbox_events
                    where published_at is null
                    order by created_at
                    limit :n
                    for update skip locked
                    """)
                    .param("n", BATCH)
                    .query((rs, i) -> new Pending(rs.getObject("id", UUID.class), rs.getString("type"),
                            rs.getString("payload")))
                    .list();
            if (batch.isEmpty()) {
                return 0;
            }
            List<UUID> ids = batch.stream().map(Pending::id).toList();
            try {
                // One confirm round-trip per batch instead of per message: same guarantee,
                // because nothing is marked published unless the whole batch was confirmed.
                rabbit.invoke(ops -> {
                    for (Pending event : batch) {
                        ops.send(EventsTopology.EXCHANGE, event.type(), toMessage(event));
                    }
                    ops.waitForConfirmsOrDie(10_000);
                    return null;
                });
            } catch (AmqpException e) {
                jdbc.sql("update outbox_events set attempts = attempts + 1, last_error = :err where id in (:ids)")
                        .param("err", truncate(e.getMessage()))
                        .param("ids", ids)
                        .update();
                log.warn("{} outbox events not published, will retry: {}", ids.size(), e.getMessage());
                return 0; // the broker is likely down; the whole batch goes again on the next tick
            }
            jdbc.sql("update outbox_events set published_at = now(), attempts = attempts + 1 where id in (:ids)")
                    .param("ids", ids)
                    .update();
            return batch.size();
        });
        return sent == null ? 0 : sent;
    }

    private static Message toMessage(Pending event) {
        var props = new MessageProperties();
        props.setMessageId(event.id().toString());
        props.setType(event.type());
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding("UTF-8");
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setTimestamp(new Date());
        return new Message(event.payload().getBytes(StandardCharsets.UTF_8), props);
    }

    private static String truncate(String s) {
        if (s == null) {
            return "unknown error";
        }
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
