package io.github.guialmm.carteira.notification;

import static io.github.guialmm.carteira.ApiTestSupport.authed;
import static io.github.guialmm.carteira.ApiTestSupport.key;
import static io.github.guialmm.carteira.ApiTestSupport.signUp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.guialmm.carteira.ApiTestSupport.Session;
import io.github.guialmm.carteira.TestcontainersConfiguration;
import io.github.guialmm.carteira.events.EventsTopology;
import io.github.guialmm.carteira.events.TransferCompleted;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotificationFlowTest {

    @Autowired MockMvc mvc;
    @Autowired RabbitTemplate rabbit;
    @Autowired JsonMapper json;
    @Autowired JdbcClient jdbc;

    @Test
    void aTransferBecomesNotificationsForBothSides() throws Exception {
        Session ana = signUp(mvc, "Ana");
        Session bruno = signUp(mvc, "Bruno");
        mvc.perform(authed(post("/api/me/deposits"), ana).header("Idempotency-Key", key())
                .content("{\"amountCents\": 100000}")).andExpect(status().isCreated());
        mvc.perform(authed(post("/api/transfers"), ana).header("Idempotency-Key", key())
                        .content("{\"destinationAccountId\": \"%s\", \"amountCents\": 123456}".formatted(bruno.accountId())))
                .andExpect(status().isUnprocessableContent()); // more than she has: no event, no notification
        mvc.perform(authed(post("/api/transfers"), ana).header("Idempotency-Key", key())
                        .content("{\"destinationAccountId\": \"%s\", \"amountCents\": 2590}".formatted(bruno.accountId())))
                .andExpect(status().isCreated());

        await().atMost(Duration.ofSeconds(10)).until(() -> messages(bruno).size() == 1 && messages(ana).size() == 2);

        assertThat(messages(bruno)).containsExactly("Você recebeu R$ 25,90 de Ana");
        assertThat(messages(ana)).containsExactly("Você enviou R$ 25,90 para Bruno", "Depósito de R$ 1.000,00 recebido");
    }

    @Test
    void aRedeliveredEventNotifiesOnlyOnce() {
        UUID account = UUID.randomUUID();
        var event = depositEvent(account, 500);
        send(event);
        send(event); // the broker or the publisher delivered it twice

        // The listener handles one message at a time, in order: once this marker is in,
        // both copies above have already been processed.
        UUID marker = UUID.randomUUID();
        send(depositEvent(marker, 1));
        await().atMost(Duration.ofSeconds(10)).until(() -> count(marker) == 1);

        assertThat(count(account)).isEqualTo(1);
    }

    @Test
    void aMalformedMessageEndsUpInTheDeadLetterQueue() {
        String id = UUID.randomUUID().toString();
        var props = new MessageProperties();
        props.setMessageId(id);
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(EventsTopology.EXCHANGE, TransferCompleted.TYPE, new Message("{not json".getBytes(StandardCharsets.UTF_8), props));

        Message dead = null;
        long until = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < until) {
            Message m = rabbit.receive(NotificationsTopology.DEAD_LETTER_QUEUE, 500);
            if (m != null && id.equals(m.getMessageProperties().getMessageId())) {
                dead = m;
                break;
            }
        }
        assertThat(dead).as("malformed message in the DLQ").isNotNull();
    }

    private List<String> messages(Session s) throws Exception {
        String body = mvc.perform(authed(get("/api/me/notifications"), s)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[*].message");
    }

    private long count(UUID account) {
        return jdbc.sql("select count(*) from notifications where account_id = ?").param(account).query(Long.class).single();
    }

    private static TransferCompleted depositEvent(UUID account, long cents) {
        return new TransferCompleted(UUID.randomUUID(), UUID.randomUUID(), "DEPOSIT", UUID.randomUUID(), null,
                account, "Teste", cents, "Depósito", Instant.now());
    }

    private void send(TransferCompleted e) {
        var props = new MessageProperties();
        props.setMessageId(e.eventId().toString());
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(EventsTopology.EXCHANGE, TransferCompleted.TYPE,
                new Message(json.writeValueAsBytes(e), props));
    }
}
