package io.github.guialmm.carteira.web;

import io.github.guialmm.carteira.account.Account;
import io.github.guialmm.carteira.account.AccountRepository;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Resolves the caller's account from the JWT subject (the user id). */
@Component
class CurrentAccount {

    private final AccountRepository accounts;

    CurrentAccount(AccountRepository accounts) {
        this.accounts = accounts;
    }

    Account of(Jwt jwt) {
        return accounts.findByOwnerId(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists"));
    }
}
