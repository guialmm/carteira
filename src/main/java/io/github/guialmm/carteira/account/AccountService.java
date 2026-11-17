package io.github.guialmm.carteira.account;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public Account openCustomerAccount(UUID ownerId) {
        return accounts.save(Account.customer(ownerId));
    }
}
