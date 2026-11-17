package io.github.guialmm.carteira.auth;

public class AuthException extends RuntimeException {

    public enum Reason {
        EMAIL_TAKEN,
        BAD_CREDENTIALS
    }

    private final Reason reason;

    public AuthException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
