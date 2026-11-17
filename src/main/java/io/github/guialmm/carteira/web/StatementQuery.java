package io.github.guialmm.carteira.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Read side of the ledger: one row per entry, newest first, with who was on the other side. */
@Component
class StatementQuery {

    record Line(long entryId, UUID transferId, String kind, long amountCents, long balanceAfterCents,
            String description, UUID counterpartAccountId, String counterpartName, Instant createdAt) {
    }

    record Page(List<Line> items, Long nextCursor) {
    }

    private final JdbcClient jdbc;

    StatementQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Page page(UUID accountId, Long cursor, int limit) {
        List<Line> lines = jdbc.sql("""
                select e.id, t.id as transfer_id, t.kind, e.amount_cents, e.balance_after_cents, t.description,
                       c.id as counterpart_id, u.name as counterpart_name, e.created_at
                from ledger_entries e
                join transfers t on t.id = e.transfer_id
                join accounts c on c.id = case when e.amount_cents < 0 then t.destination_account_id
                                               else t.source_account_id end
                left join users u on u.id = c.owner_id
                where e.account_id = :account and e.id < :cursor
                order by e.id desc
                limit :limit
                """)
                .param("account", accountId)
                .param("cursor", cursor == null ? Long.MAX_VALUE : cursor)
                .param("limit", limit + 1)
                .query((rs, i) -> new Line(
                        rs.getLong("id"),
                        rs.getObject("transfer_id", UUID.class),
                        rs.getString("kind"),
                        rs.getLong("amount_cents"),
                        rs.getLong("balance_after_cents"),
                        rs.getString("description"),
                        rs.getObject("counterpart_id", UUID.class),
                        rs.getString("counterpart_name"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
        if (lines.size() <= limit) {
            return new Page(lines, null);
        }
        List<Line> page = lines.subList(0, limit);
        return new Page(page, page.getLast().entryId());
    }
}
