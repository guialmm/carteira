package io.github.guialmm.carteira.pix;

public class PixKeyException extends RuntimeException {

    public enum Reason {
        INVALID_KEY,
        KEY_TAKEN,
        KEY_LIMIT_REACHED,
        KEY_NOT_FOUND,
        EMAIL_NOT_YOURS
    }

    private final Reason reason;

    public PixKeyException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
