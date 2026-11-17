package io.github.guialmm.carteira.transfer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByInitiatorAccountIdAndIdempotencyKey(UUID initiatorAccountId, String idempotencyKey);
}
