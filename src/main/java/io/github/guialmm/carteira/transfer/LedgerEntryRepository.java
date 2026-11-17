package io.github.guialmm.carteira.transfer;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    @Query("select coalesce(sum(e.amountCents), 0) from LedgerEntry e where e.accountId = :accountId")
    long sumByAccountId(@Param("accountId") UUID accountId);
}
