package io.github.guialmm.carteira.transfer;

/** A transfer the ledger refused. Nothing was written when this is thrown. */
public class TransferException extends RuntimeException {

    public enum Reason {
        ACCOUNT_NOT_FOUND,
        SAME_ACCOUNT,
        INVALID_AMOUNT,
        INSUFFICIENT_FUNDS,
        IDEMPOTENCY_KEY_REUSED
    }

    private final Reason reason;

    public TransferException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
