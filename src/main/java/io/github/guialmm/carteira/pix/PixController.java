package io.github.guialmm.carteira.pix;

import io.github.guialmm.carteira.account.Account;
import io.github.guialmm.carteira.account.AccountRepository;
import io.github.guialmm.carteira.transfer.TransferResult;
import io.github.guialmm.carteira.transfer.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@Tag(name = "Pix")
@SecurityRequirement(name = "bearer")
class PixController {

    private final PixKeyService pix;
    private final TransferService transfers;
    private final AccountRepository accounts;

    PixController(PixKeyService pix, TransferService transfers, AccountRepository accounts) {
        this.pix = pix;
        this.transfers = transfers;
        this.accounts = accounts;
    }

    record KeyRequest(@NotNull PixKeyType type, @Size(max = 77) String value) {
    }

    record KeyView(UUID id, PixKeyType type, String value, Instant createdAt) {

        static KeyView of(PixKey k) {
            return new KeyView(k.getId(), k.getType(), k.getValue(), k.getCreatedAt());
        }
    }

    /** What a payer sees before confirming: the name, and the key half hidden. */
    record LookupView(String name, PixKeyType keyType, String maskedKey) {
    }

    record PixTransferRequest(
            @NotBlank @Size(max = 77) String key,
            @NotNull @Positive Long amountCents,
            @Size(max = 140) String description) {
    }

    record PixTransferView(UUID id, String recipientName, long amountCents, String description, Instant createdAt) {
    }

    @PostMapping("/me/pix-keys")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra uma chave Pix (para RANDOM, deixe value vazio)")
    KeyView register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody KeyRequest body) {
        return KeyView.of(pix.register(accountOf(jwt), body.type(), body.value()));
    }

    @GetMapping("/me/pix-keys")
    @Operation(summary = "Lista suas chaves Pix")
    List<KeyView> list(@AuthenticationPrincipal Jwt jwt) {
        return pix.list(accountOf(jwt)).stream().map(KeyView::of).toList();
    }

    @DeleteMapping("/me/pix-keys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove uma chave Pix sua")
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        pix.delete(accountOf(jwt), id);
    }

    @GetMapping("/pix/lookup")
    @Operation(summary = "Mostra de quem é a chave antes de pagar")
    LookupView lookup(@RequestParam String key) {
        var parsed = PixKeys.detect(key);
        var owner = pix.resolve(key);
        return new LookupView(owner.name(), parsed.type(), PixKeys.mask(parsed.type(), parsed.value()));
    }

    @PostMapping("/pix/transfers")
    @Operation(summary = "Paga para uma chave Pix")
    ResponseEntity<PixTransferView> pay(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 64) String idempotencyKey,
            @Valid @RequestBody PixTransferRequest body) {
        var owner = pix.resolve(body.key());
        String description = body.description() == null || body.description().isBlank() ? null : body.description().strip();
        TransferResult result = transfers.transfer(accountOf(jwt), owner.accountId(), body.amountCents(),
                description, idempotencyKey);
        var t = result.transfer();
        var view = new PixTransferView(t.getId(), owner.name(), t.getAmountCents(), t.getDescription(), t.getCreatedAt());
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(view);
        }
        return ResponseEntity.created(URI.create("/api/transfers/" + t.getId())).body(view);
    }

    private UUID accountOf(Jwt jwt) {
        return accounts.findByOwnerId(UUID.fromString(jwt.getSubject()))
                .map(Account::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists"));
    }
}
