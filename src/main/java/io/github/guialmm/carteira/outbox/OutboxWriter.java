package io.github.guialmm.carteira.outbox;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OutboxWriter {

    private final OutboxRepository outbox;
    private final JsonMapper json;

    public OutboxWriter(OutboxRepository outbox, JsonMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    /**
     * MANDATORY: appending outside a transaction is a bug, because the event could then be
     * stored without the change it describes (or the other way around).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(UUID eventId, String type, UUID aggregateId, Object event) {
        outbox.save(new OutboxEvent(eventId, type, aggregateId, json.writeValueAsString(event)));
    }
}
