package io.github.guialmm.carteira.pix;

import static io.github.guialmm.carteira.pix.PixKeyException.Reason.INVALID_KEY;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Parsing and normalization of Pix keys, following the formats the Central Bank uses. */
public final class PixKeys {

    public record Parsed(PixKeyType type, String value) {
    }

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    // Mobile numbers only: +55, two-digit area code (no zero), 9 followed by 8 digits.
    private static final Pattern PHONE = Pattern.compile("^\\+55[1-9]{2}9\\d{8}$");
    private static final Pattern UUID_FORMAT =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private PixKeys() {
    }

    /** Parses a key of a known type. */
    public static Parsed parse(PixKeyType type, String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("Key is empty");
        }
        String s = raw.strip();
        return switch (type) {
            case EMAIL -> {
                String email = s.toLowerCase(Locale.ROOT);
                if (email.length() > 77 || !EMAIL.matcher(email).matches()) {
                    throw invalid("Invalid e-mail key");
                }
                yield new Parsed(type, email);
            }
            case PHONE -> {
                String phone = s.replaceAll("[\\s().-]", "");
                if (!PHONE.matcher(phone).matches()) {
                    throw invalid("Phone keys look like +5535998765432");
                }
                yield new Parsed(type, phone);
            }
            case CPF -> {
                String cpf = s.replaceAll("[.-]", "");
                if (!isValidCpf(cpf)) {
                    throw invalid("Invalid CPF");
                }
                yield new Parsed(type, cpf);
            }
            case RANDOM -> {
                String uuid = s.toLowerCase(Locale.ROOT);
                if (!UUID_FORMAT.matcher(uuid).matches()) {
                    throw invalid("Random keys are UUIDs");
                }
                yield new Parsed(type, uuid);
            }
        };
    }

    /**
     * Guesses the type the way a banking app's "Pix key" field does: '@' means e-mail, '+' means
     * phone, a UUID is a random key, and 11 digits are a CPF. Phones must carry +55, which is what
     * keeps them from being confused with CPFs.
     */
    public static Parsed detect(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("Key is empty");
        }
        String s = raw.strip();
        if (s.contains("@")) {
            return parse(PixKeyType.EMAIL, s);
        }
        if (s.startsWith("+")) {
            return parse(PixKeyType.PHONE, s);
        }
        if (UUID_FORMAT.matcher(s.toLowerCase(Locale.ROOT)).matches()) {
            return parse(PixKeyType.RANDOM, s);
        }
        return parse(PixKeyType.CPF, s);
    }

    public static String newRandomKey() {
        return UUID.randomUUID().toString();
    }

    /** Hides most of the key, as banks do on confirmation screens: ***.456.789-** for a CPF. */
    public static String mask(PixKeyType type, String value) {
        return switch (type) {
            case CPF -> "***." + value.substring(3, 6) + "." + value.substring(6, 9) + "-**";
            case PHONE -> value.substring(0, 5) + "*****" + value.substring(value.length() - 4);
            case EMAIL -> {
                int at = value.indexOf('@');
                yield value.charAt(0) + "***" + value.substring(at);
            }
            case RANDOM -> value.substring(0, 8) + "-****-****-****-" + value.substring(value.length() - 4);
        };
    }

    static boolean isValidCpf(String cpf) {
        if (cpf.length() != 11 || !cpf.chars().allMatch(Character::isDigit)) {
            return false;
        }
        if (cpf.chars().distinct().count() == 1) {
            return false; // 000.000.000-00, 111.111.111-11... pass the checksum but are not real
        }
        return checkDigit(cpf, 9) == cpf.charAt(9) - '0' && checkDigit(cpf, 10) == cpf.charAt(10) - '0';
    }

    private static int checkDigit(String cpf, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (cpf.charAt(i) - '0') * (length + 1 - i);
        }
        int rest = (sum * 10) % 11;
        return rest == 10 ? 0 : rest;
    }

    private static PixKeyException invalid(String message) {
        return new PixKeyException(INVALID_KEY, message);
    }
}
