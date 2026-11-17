package io.github.guialmm.carteira.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.guialmm.carteira.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WalletApiTest {

    @Autowired MockMvc mvc;

    record Session(String token, String accountId) {
    }

    @Test
    void depositTransferAndStatementEndToEnd() throws Exception {
        Session alice = signUp("Alice");
        Session bob = signUp("Bob");

        mvc.perform(authed(post("/api/me/deposits"), alice).header("Idempotency-Key", key())
                        .content("{\"amountCents\": 50000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("DEPOSIT"));

        String transferKey = key();
        String body = "{\"destinationAccountId\": \"%s\", \"amountCents\": 12345, \"description\": \"pizza\"}"
                .formatted(bob.accountId());
        String transferId = JsonPath.read(mvc.perform(authed(post("/api/transfers"), alice)
                        .header("Idempotency-Key", transferKey).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/transfers/")))
                .andReturn().getResponse().getContentAsString(), "$.id");

        // A client retry with the same key gets the same transfer back, not a second debit.
        mvc.perform(authed(post("/api/transfers"), alice).header("Idempotency-Key", transferKey).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(transferId));

        mvc.perform(authed(get("/api/me"), alice))
                .andExpect(jsonPath("$.balanceCents").value(50000 - 12345));
        mvc.perform(authed(get("/api/me"), bob))
                .andExpect(jsonPath("$.balanceCents").value(12345))
                .andExpect(jsonPath("$.name").value("Bob"));

        mvc.perform(authed(get("/api/me/statement"), alice))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].amountCents").value(-12345))
                .andExpect(jsonPath("$.items[0].counterpartName").value("Bob"))
                .andExpect(jsonPath("$.items[0].balanceAfterCents").value(50000 - 12345))
                .andExpect(jsonPath("$.items[1].kind").value("DEPOSIT"));

        mvc.perform(authed(get("/api/transfers/" + transferId), bob)).andExpect(status().isOk());
    }

    @Test
    void statementPagesWithCursor() throws Exception {
        Session alice = signUp("Alice");
        for (int i = 0; i < 3; i++) {
            mvc.perform(authed(post("/api/me/deposits"), alice).header("Idempotency-Key", key())
                    .content("{\"amountCents\": 100}")).andExpect(status().isCreated());
        }
        String first = mvc.perform(authed(get("/api/me/statement?limit=2"), alice))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        Number cursor = JsonPath.read(first, "$.nextCursor");

        mvc.perform(authed(get("/api/me/statement?limit=2&cursor=" + cursor), alice))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void domainErrorsAreProblemDetailsWithACode() throws Exception {
        Session alice = signUp("Alice");
        Session bob = signUp("Bob");
        String body = "{\"destinationAccountId\": \"%s\", \"amountCents\": 1}".formatted(bob.accountId());

        mvc.perform(authed(post("/api/transfers"), alice).header("Idempotency-Key", key()).content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        mvc.perform(authed(post("/api/transfers"), alice).header("Idempotency-Key", key())
                        .content("{\"destinationAccountId\": \"%s\", \"amountCents\": 1}".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        // The system funding account is not a valid destination.
        mvc.perform(authed(post("/api/transfers"), alice).header("Idempotency-Key", key())
                        .content("{\"destinationAccountId\": \"00000000-0000-0000-0000-000000000001\", \"amountCents\": 1}"))
                .andExpect(status().isNotFound());

        String key = key();
        mvc.perform(authed(post("/api/me/deposits"), alice).header("Idempotency-Key", key)
                .content("{\"amountCents\": 100}")).andExpect(status().isCreated());
        mvc.perform(authed(post("/api/me/deposits"), alice).header("Idempotency-Key", key)
                        .content("{\"amountCents\": 200}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        Session alice = signUp("Alice");

        mvc.perform(authed(post("/api/transfers"), alice).content("{\"amountCents\": 1}"))
                .andExpect(status().isBadRequest()); // missing Idempotency-Key
        mvc.perform(authed(post("/api/me/deposits"), alice).header("Idempotency-Key", key())
                        .content("{\"amountCents\": -5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(authed(post("/api/me/deposits"), alice).header("Idempotency-Key", key())
                        .content("{\"amountCents\": 100001}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedRoutesNeedAValidToken() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void otherPeoplesTransfersAreNotVisible() throws Exception {
        Session alice = signUp("Alice");
        Session bob = signUp("Bob");
        Session eve = signUp("Eve");
        mvc.perform(authed(post("/api/me/deposits"), alice).header("Idempotency-Key", key())
                .content("{\"amountCents\": 1000}"));
        String id = JsonPath.read(mvc.perform(authed(post("/api/transfers"), alice).header("Idempotency-Key", key())
                        .content("{\"destinationAccountId\": \"%s\", \"amountCents\": 10}".formatted(bob.accountId())))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(authed(get("/api/transfers/" + id), eve)).andExpect(status().isNotFound());
    }

    @Test
    void registrationAndLoginRules() throws Exception {
        String email = "Case-" + UUID.randomUUID() + "@Test.dev";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ana\", \"email\": \"%s\", \"password\": \"senha-forte-1\"}".formatted(email)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ana\", \"email\": \"%s\", \"password\": \"senha-forte-1\"}"
                                .formatted(email.toLowerCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Ana\", \"email\": \"x@test.dev\", \"password\": \"curta\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"errada-123\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody-%s@test.dev\", \"password\": \"errada-123\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDocsArePublic() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/transfers']").exists());
    }

    private Session signUp(String name) throws Exception {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@test.dev";
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\", \"email\": \"%s\", \"password\": \"senha-forte-1\"}".formatted(name, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"senha-forte-1\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Session(JsonPath.read(login, "$.accessToken"), JsonPath.read(registered, "$.accountId"));
    }

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder req, Session s) {
        return req.header("Authorization", "Bearer " + s.token()).contentType(MediaType.APPLICATION_JSON);
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
