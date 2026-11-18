package io.github.guialmm.carteira.notification;

import io.github.guialmm.carteira.events.TransferCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
class TransferCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(TransferCompletedListener.class);

    private final NotificationService notifications;
    private final JsonMapper json;

    TransferCompletedListener(NotificationService notifications, JsonMapper json) {
        this.notifications = notifications;
        this.json = json;
    }

    @RabbitListener(queues = NotificationsTopology.QUEUE)
    void on(Message message) {
        TransferCompleted event;
        try {
            event = json.readValue(message.getBody(), TransferCompleted.class);
        } catch (JacksonException e) {
            // Retrying cannot fix a malformed message: send it straight to the dead-letter queue.
            throw new AmqpRejectAndDontRequeueException("Malformed event " + message.getMessageProperties().getMessageId(), e);
        }
        if (!notifications.handle(event)) {
            log.info("Duplicate event {} ignored", event.eventId());
        }
    }
}
