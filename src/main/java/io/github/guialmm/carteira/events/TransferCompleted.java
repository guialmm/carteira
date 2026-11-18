package io.github.guialmm.carteira.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published once per committed transfer or deposit. This is the contract consumers depend on, so
 * it carries everything they need (names included) instead of making them read our tables.
 */
public record TransferCompleted(
        UUID eventId,
        UUID transferId,
        String kind,
        UUID sourceAccountId,
        String sourceName,
        UUID destinationAccountId,
        String destinationName,
        long amountCents,
        String description,
        Instant occurredAt) {

    public static final String TYPE = "transfer.completed";
}
