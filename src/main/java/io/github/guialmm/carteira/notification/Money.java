package io.github.guialmm.carteira.notification;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

final class Money {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private Money() {
    }

    /** 123456 → "R$ 1.234,56" */
    static String brl(long cents) {
        var format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        return "R$ " + format.format(cents / 100.0);
    }
}
