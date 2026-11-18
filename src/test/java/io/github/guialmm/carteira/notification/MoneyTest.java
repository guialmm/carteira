package io.github.guialmm.carteira.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1        | R$ 0,01",
            "2590     | R$ 25,90",
            "100000   | R$ 1.000,00",
            "123456789| R$ 1.234.567,89",
    })
    void formatsCentsAsBrazilianReais(long cents, String expected) {
        assertThat(Money.brl(cents)).isEqualTo(expected);
    }
}
