package io.github.guialmm.carteira.pix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PixKeysTest {

    @ParameterizedTest
    @CsvSource({
            "Maria@Exemplo.com.br,     EMAIL,  maria@exemplo.com.br",
            "+55 (35) 99876-5432,      PHONE,  +5535998765432",
            "529.982.247-25,           CPF,    52998224725",
            "52998224725,              CPF,    52998224725",
            "3F2504E0-4F89-41D3-9A0C-0305E82C3301, RANDOM, 3f2504e0-4f89-41d3-9a0c-0305e82c3301",
    })
    void detectsAndNormalizes(String raw, PixKeyType type, String normalized) {
        var parsed = PixKeys.detect(raw);
        assertThat(parsed.type()).isEqualTo(type);
        assertThat(parsed.value()).isEqualTo(normalized);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "529.982.247-24",   // wrong check digit
            "111.111.111-11",   // passes the checksum, but is a known fake
            "1234567890",       // 10 digits
            "35998765432",      // phone without +55 is read as a CPF, and fails
            "+5535898765432",   // landline-style number, not a mobile
            "+5505998765432",   // area code cannot start with zero
            "maria@",           // incomplete e-mail
            "   ",
    })
    void rejectsInvalidKeys(String raw) {
        assertThatThrownBy(() -> PixKeys.detect(raw)).isInstanceOf(PixKeyException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "CPF,    52998224725,                           ***.982.247-**",
            "PHONE,  +5535998765432,                        +5535*****5432",
            "EMAIL,  maria@exemplo.com.br,                  m***@exemplo.com.br",
            "RANDOM, 3f2504e0-4f89-41d3-9a0c-0305e82c3301,  3f2504e0-****-****-****-3301",
    })
    void masksKeysForConfirmationScreens(PixKeyType type, String value, String masked) {
        assertThat(PixKeys.mask(type, value)).isEqualTo(masked);
    }
}
