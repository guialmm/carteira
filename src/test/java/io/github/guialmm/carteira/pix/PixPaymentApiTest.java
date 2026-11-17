package io.github.guialmm.carteira.pix;

import static io.github.guialmm.carteira.ApiTestSupport.authed;
import static io.github.guialmm.carteira.ApiTestSupport.key;
import static io.github.guialmm.carteira.ApiTestSupport.randomCpf;
import static io.github.guialmm.carteira.ApiTestSupport.signUp;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.guialmm.carteira.ApiTestSupport.Session;
import io.github.guialmm.carteira.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PixPaymentApiTest {

    @Autowired MockMvc mvc;

    @Test
    void looksUpTheKeyThenPaysIt() throws Exception {
        Session ana = signUp(mvc, "Ana");
        Session bruno = signUp(mvc, "Bruno");
        String cpf = randomCpf();
        mvc.perform(authed(post("/api/me/pix-keys"), bruno).content("{\"type\": \"CPF\", \"value\": \"%s\"}".formatted(cpf)))
                .andExpect(status().isCreated());
        mvc.perform(authed(post("/api/me/deposits"), ana).header("Idempotency-Key", key())
                .content("{\"amountCents\": 10000}")).andExpect(status().isCreated());

        // Typed with punctuation, as people do: the lookup still finds it and hides most digits.
        String typed = cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
        mvc.perform(authed(get("/api/pix/lookup").param("key", typed), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bruno"))
                .andExpect(jsonPath("$.keyType").value("CPF"))
                .andExpect(jsonPath("$.maskedKey").value("***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**"));

        String idem = key();
        String body = "{\"key\": \"%s\", \"amountCents\": 2590, \"description\": \"café\"}".formatted(typed);
        mvc.perform(authed(post("/api/pix/transfers"), ana).header("Idempotency-Key", idem).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipientName").value("Bruno"));
        mvc.perform(authed(post("/api/pix/transfers"), ana).header("Idempotency-Key", idem).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        mvc.perform(authed(get("/api/me"), bruno)).andExpect(jsonPath("$.balanceCents").value(2590));
        mvc.perform(authed(get("/api/me"), ana)).andExpect(jsonPath("$.balanceCents").value(10000 - 2590));
    }

    @Test
    void unknownKeysAreNotFound() throws Exception {
        Session ana = signUp(mvc, "Ana");

        mvc.perform(authed(get("/api/pix/lookup").param("key", randomCpf()), ana))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KEY_NOT_FOUND"));
        mvc.perform(authed(post("/api/pix/transfers"), ana).header("Idempotency-Key", key())
                        .content("{\"key\": \"+5535912345678\", \"amountCents\": 100}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void payingYourOwnKeyIsRefused() throws Exception {
        Session ana = signUp(mvc, "Ana");
        mvc.perform(authed(post("/api/me/pix-keys"), ana).content("{\"type\": \"EMAIL\", \"value\": \"%s\"}".formatted(ana.email())));
        mvc.perform(authed(post("/api/me/deposits"), ana).header("Idempotency-Key", key())
                .content("{\"amountCents\": 500}"));

        mvc.perform(authed(post("/api/pix/transfers"), ana).header("Idempotency-Key", key())
                        .content("{\"key\": \"%s\", \"amountCents\": 100}".formatted(ana.email())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT"));
    }

    @Test
    void lookupNeedsLogin() throws Exception {
        mvc.perform(get("/api/pix/lookup").param("key", randomCpf())).andExpect(status().isUnauthorized());
    }
}
