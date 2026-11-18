package io.github.guialmm.carteira.events;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The exchange every domain event goes to. Consumers bind their own queues to it. */
@Configuration
public class EventsTopology {

    public static final String EXCHANGE = "carteira.events";

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }
}
