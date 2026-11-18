package io.github.guialmm.carteira.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import io.github.guialmm.carteira.TestcontainersConfiguration;
import io.github.guialmm.carteira.account.AccountService;
import io.github.guialmm.carteira.events.EventsTopology;
import io.github.guialmm.carteira.events.TransferCompleted;
import io.github.guialmm.carteira.transfer.TransferService;
import io.github.guialmm.carteira.user.User;
import io.github.guialmm.carteira.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxPublisherTest {

    @Autowired TransferService transfers;
    @Autowired AccountService accounts;
    @Autowired UserRepository users;
    @Autowired OutboxRepository outbox;
    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;

    String queue;

    @BeforeEach
    void listenToTransferEvents() {
        queue = "test." + UUID.randomUUID();
        admin.declareQueue(new Queue(queue, true, false, false));
        Binding binding = BindingBuilder.bind(new Queue(queue))
                .to(new TopicExchange(EventsTopology.EXCHANGE)).with(TransferCompleted.TYPE);
        admin.declareBinding(binding);
    }

    @AfterEach
    void dropQueue() {
        admin.deleteQueue(queue);
    }

    @Test
    void committedTransfersReachTheBrokerAndAreMarkedPublished() {
        UUID ana = open("Ana", 10_000);
        UUID bruno = open("Bruno", 0);
        UUID transferId = transfers.transfer(ana, bruno, 4_200, "show", UUID.randomUUID().toString())
                .transfer().getId();

        List<Message> received = new ArrayList<>();
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            Message m = rabbit.receive(queue, 200);
            if (m != null) {
                received.add(m);
            }
            return received.stream().anyMatch(msg -> body(msg).contains(transferId.toString()));
        });

        Message message = received.stream().filter(m -> body(m).contains(transferId.toString())).findFirst().orElseThrow();
        var event = outbox.findByAggregateId(transferId).getFirst();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.getId().toString());
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo(TransferCompleted.TYPE);
        assertThat((String) JsonPath.read(body(message), "$.destinationName")).isEqualTo("Bruno");

        await().atMost(Duration.ofSeconds(5))
                .until(() -> outbox.findById(event.getId()).orElseThrow().getPublishedAt() != null);
    }

    private static String body(Message m) {
        return new String(m.getBody(), StandardCharsets.UTF_8);
    }

    private UUID open(String name, long cents) {
        var user = users.save(new User("u-" + UUID.randomUUID() + "@test.dev", name, "x"));
        UUID id = accounts.openCustomerAccount(user.getId()).getId();
        if (cents > 0) {
            transfers.deposit(id, cents, UUID.randomUUID().toString());
        }
        return id;
    }
}
