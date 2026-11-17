package io.github.guialmm.carteira.transfer;

/** {@code replayed} is true when the idempotency key matched an earlier, identical request. */
public record TransferResult(Transfer transfer, boolean replayed) {
}
