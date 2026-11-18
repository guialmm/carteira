package io.github.guialmm.carteira.notification;

import io.github.guialmm.carteira.events.TransferCompleted;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class NotificationsTopology {

    static final String QUEUE = "notifications.transfer-completed";
    static final String DEAD_LETTER_EXCHANGE = "carteira.dead-letter";
    static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";

    /** Quorum queue: replicated and durable, the type RabbitMQ recommends for data you cannot lose. */
    @Bean
    Queue notificationsQueue() {
        return QueueBuilder.durable(QUEUE)
                .quorum()
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    Binding notificationsBinding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue).to(eventsExchange).with(TransferCompleted.TYPE);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).quorum().build();
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DEAD_LETTER_QUEUE);
    }

}
