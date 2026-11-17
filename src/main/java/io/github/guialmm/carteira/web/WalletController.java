package io.github.guialmm.carteira.web;

import io.github.guialmm.carteira.account.Account;
import io.github.guialmm.carteira.transfer.Transfer;
import io.github.guialmm.carteira.transfer.TransferRepository;
import io.github.guialmm.carteira.transfer.TransferResult;
import io.github.guialmm.carteira.transfer.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@Tag(name = "Carteira")
@SecurityRequirement(name = "bearer")
class WalletController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";

    private final CurrentAccount current;
    private final TransferService service;
    private final TransferRepository transfers;
    private final StatementQuery statement;

    WalletController(CurrentAccount current, TransferService service, TransferRepository transfers,
            StatementQuery statement) {
        this.current = current;
        this.service = service;
        this.transfers = transfers;
        this.statement = statement;
    }

    record AccountView(UUID accountId, String name, long balanceCents, Instant createdAt) {
    }

    record DepositRequest(@NotNull @Positive @Max(TransferService.MAX_DEPOSIT_CENTS) Long amountCents) {
    }

    record TransferRequest(
            @NotNull UUID destinationAccountId,
            @NotNull @Positive Long amountCents,
            @Size(max = 140) String description) {
    }

    record TransferView(UUID id, String kind, UUID sourceAccountId, UUID destinationAccountId,
            long amountCents, String description, Instant createdAt) {

        static TransferView of(Transfer t) {
            return new TransferView(t.getId(), t.getKind().name(), t.getSourceAccountId(),
                    t.getDestinationAccountId(), t.getAmountCents(), t.getDescription(), t.getCreatedAt());
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Conta e saldo de quem está logado")
    AccountView me(@AuthenticationPrincipal Jwt jwt) {
        Account account = current.of(jwt);
        return new AccountView(account.getId(), jwt.getClaimAsString("name"), account.getBalanceCents(),
                account.getCreatedAt());
    }

    @PostMapping("/me/deposits")
    @Operation(summary = "Depósito de demonstração (até R$ 1.000,00), vindo da conta do sistema")
    ResponseEntity<TransferView> deposit(@AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mesma chave = mesmo depósito, mesmo se a requisição for repetida")
            @RequestHeader(IDEMPOTENCY_KEY) @Size(min = 8, max = 64) String key,
            @Valid @RequestBody DepositRequest body) {
        return respond(service.deposit(current.of(jwt).getId(), body.amountCents(), key));
    }

    @PostMapping("/transfers")
    @Operation(summary = "Transfere da sua conta para outra")
    ResponseEntity<TransferView> transfer(@AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Mesma chave = mesma transferência, mesmo se a requisição for repetida")
            @RequestHeader(IDEMPOTENCY_KEY) @Size(min = 8, max = 64) String key,
            @Valid @RequestBody TransferRequest body) {
        UUID source = current.of(jwt).getId();
        return respond(service.transfer(source, body.destinationAccountId(), body.amountCents(),
                blankToNull(body.description()), key));
    }

    @GetMapping("/transfers/{id}")
    @Operation(summary = "Detalhe de uma transferência em que você participou")
    TransferView transfer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID mine = current.of(jwt).getId();
        return transfers.findById(id)
                .filter(t -> t.getSourceAccountId().equals(mine) || t.getDestinationAccountId().equals(mine))
                .map(TransferView::of)
                // 404 for "not yours" too, so ids of other people's transfers leak nothing
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer not found"));
    }

    @GetMapping("/me/statement")
    @Operation(summary = "Extrato, do mais recente para o mais antigo, paginado por cursor")
    StatementQuery.Page statement(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return statement.page(current.of(jwt).getId(), cursor, limit);
    }

    private static ResponseEntity<TransferView> respond(TransferResult result) {
        var view = TransferView.of(result.transfer());
        if (result.replayed()) {
            return ResponseEntity.ok().header(REPLAYED, "true").body(view);
        }
        return ResponseEntity.created(URI.create("/api/transfers/" + view.id())).body(view);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
