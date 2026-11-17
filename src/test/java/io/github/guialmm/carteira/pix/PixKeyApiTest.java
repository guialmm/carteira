package io.github.guialmm.carteira.pix;

import static io.github.guialmm.carteira.ApiTestSupport.authed;
import static io.github.guialmm.carteira.ApiTestSupport.randomCpf;
import static io.github.guialmm.carteira.ApiTestSupport.randomMobile;
import static io.github.guialmm.carteira.ApiTestSupport.signUp;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.guialmm.carteira.ApiTestSupport.Session;
import io.github.guialmm.carteira.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PixKeyApiTest {

    @Autowired MockMvc mvc;

    @Test
    void registersEveryKeyTypeNormalized() throws Exception {
        Session ana = signUp(mvc, "Ana");
        String cpf = randomCpf();
        String formattedCpf = cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);

        addKey(ana, "CPF", formattedCpf).andExpect(status().isCreated()).andExpect(jsonPath("$.value").value(cpf));
        addKey(ana, "EMAIL", ana.email().toUpperCase()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value(ana.email()));
        addKey(ana, "PHONE", randomMobile()).andExpect(status().isCreated());
        addKey(ana, "RANDOM", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value(matchesPattern("[0-9a-f-]{36}")));

        mvc.perform(authed(get("/api/me/pix-keys"), ana))
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].type").value("CPF"));
    }

    @Test
    void aKeyBelongsToOneAccountOnly() throws Exception {
        Session ana = signUp(mvc, "Ana");
        Session bia = signUp(mvc, "Bia");
        String cpf = randomCpf();
        addKey(ana, "CPF", cpf).andExpect(status().isCreated());

        addKey(bia, "CPF", cpf).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("KEY_TAKEN"));
    }

    @Test
    void onlyYourOwnEmailCanBeAKey() throws Exception {
        Session ana = signUp(mvc, "Ana");
        Session bia = signUp(mvc, "Bia");

        addKey(ana, "EMAIL", bia.email())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_YOURS"));
    }

    @Test
    void rejectsMalformedKeys() throws Exception {
        Session ana = signUp(mvc, "Ana");

        addKey(ana, "CPF", "529.982.247-24").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_KEY"));
        addKey(ana, "PHONE", "35998765432").andExpect(status().isBadRequest());
        addKey(ana, "RANDOM", "eu-escolho-a-minha").andExpect(status().isBadRequest());
    }

    @Test
    void anAccountHasAtMostFiveKeys() throws Exception {
        Session ana = signUp(mvc, "Ana");
        for (int i = 0; i < PixKeyService.MAX_KEYS_PER_ACCOUNT; i++) {
            addKey(ana, "RANDOM", null).andExpect(status().isCreated());
        }

        addKey(ana, "RANDOM", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("KEY_LIMIT_REACHED"));
    }

    @Test
    void deletesOnlyYourOwnKeys() throws Exception {
        Session ana = signUp(mvc, "Ana");
        Session bia = signUp(mvc, "Bia");
        String id = JsonPath.read(addKey(ana, "RANDOM", null).andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(authed(delete("/api/me/pix-keys/" + id), bia)).andExpect(status().isNotFound());
        mvc.perform(authed(delete("/api/me/pix-keys/" + id), ana)).andExpect(status().isNoContent());
        mvc.perform(authed(get("/api/me/pix-keys"), ana)).andExpect(jsonPath("$", hasSize(0)));
    }

    private ResultActions addKey(Session s, String type, String value) throws Exception {
        String body = value == null
                ? "{\"type\": \"%s\"}".formatted(type)
                : "{\"type\": \"%s\", \"value\": \"%s\"}".formatted(type, value);
        return mvc.perform(authed(post("/api/me/pix-keys"), s).content(body));
    }
}
