package io.github.guialmm.carteira.notification;

import io.github.guialmm.carteira.account.Account;
import io.github.guialmm.carteira.account.AccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Tag(name = "Notificações")
@SecurityRequirement(name = "bearer")
class NotificationController {

    private final NotificationService notifications;
    private final AccountRepository accounts;

    NotificationController(NotificationService notifications, AccountRepository accounts) {
        this.notifications = notifications;
        this.accounts = accounts;
    }

    @GetMapping("/api/me/notifications")
    @Operation(summary = "Notificações mais recentes, geradas a partir dos eventos de transferência")
    List<NotificationService.Notification> latest(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        UUID account = accounts.findByOwnerId(UUID.fromString(jwt.getSubject()))
                .map(Account::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists"));
        return notifications.latest(account, limit);
    }
}
