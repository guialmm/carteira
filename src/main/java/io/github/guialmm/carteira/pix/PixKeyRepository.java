package io.github.guialmm.carteira.pix;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PixKeyRepository extends JpaRepository<PixKey, UUID> {

    List<PixKey> findByAccountIdOrderByCreatedAt(UUID accountId);

    long countByAccountId(UUID accountId);

    Optional<PixKey> findByTypeAndValue(PixKeyType type, String value);

    boolean existsByTypeAndValue(PixKeyType type, String value);
}
