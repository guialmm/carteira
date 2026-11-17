package io.github.guialmm.carteira;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Helpers for API tests: sign up through the real endpoints and send authenticated requests. */
public final class ApiTestSupport {

    public record Session(String token, String accountId, String email) {
    }

    private ApiTestSupport() {
    }

    public static Session signUp(MockMvc mvc, String name) throws Exception {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@test.dev";
        String registered = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\", \"email\": \"%s\", \"password\": \"senha-forte-1\"}".formatted(name, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"senha-forte-1\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Session(JsonPath.read(login, "$.accessToken"), JsonPath.read(registered, "$.accountId"), email);
    }

    public static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder req, Session s) {
        return req.header("Authorization", "Bearer " + s.token()).contentType(MediaType.APPLICATION_JSON);
    }

    public static String key() {
        return UUID.randomUUID().toString();
    }

    /** A random CPF with valid check digits, so tests never collide on the unique key. */
    public static String randomCpf() {
        var rnd = ThreadLocalRandom.current();
        int[] d = new int[11];
        for (int i = 0; i < 9; i++) {
            d[i] = rnd.nextInt(10);
        }
        d[9] = checkDigit(d, 9);
        d[10] = checkDigit(d, 10);
        var sb = new StringBuilder();
        for (int x : d) {
            sb.append(x);
        }
        return sb.toString();
    }

    public static String randomMobile() {
        return "+55359" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    private static int checkDigit(int[] d, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += d[i] * (length + 1 - i);
        }
        int rest = (sum * 10) % 11;
        return rest == 10 ? 0 : rest;
    }
}
