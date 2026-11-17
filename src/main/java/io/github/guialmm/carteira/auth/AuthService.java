package io.github.guialmm.carteira.auth;

import io.github.guialmm.carteira.account.Account;
import io.github.guialmm.carteira.account.AccountService;
import io.github.guialmm.carteira.user.User;
import io.github.guialmm.carteira.user.UserRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final AccountService accounts;
    private final PasswordEncoder passwords;
    private final JwtEncoder jwt;
    private final JwtProperties props;
    // Compared against when the e-mail does not exist, so both paths cost one bcrypt check.
    private final String dummyHash;

    public AuthService(UserRepository users, AccountService accounts, PasswordEncoder passwords,
            JwtEncoder jwt, JwtProperties props) {
        this.users = users;
        this.accounts = accounts;
        this.passwords = passwords;
        this.jwt = jwt;
        this.props = props;
        this.dummyHash = passwords.encode("timing-equalizer");
    }

    public record Registration(User user, Account account) {
    }

    public record Token(String accessToken, long expiresIn) {
    }

    @Transactional
    public Registration register(String name, String email, String password) {
        String normalized = normalize(email);
        if (users.existsByEmail(normalized)) {
            throw new AuthException(AuthException.Reason.EMAIL_TAKEN, "E-mail already registered");
        }
        try {
            User user = users.saveAndFlush(new User(normalized, name.strip(), passwords.encode(password)));
            return new Registration(user, accounts.openCustomerAccount(user.getId()));
        } catch (DataIntegrityViolationException race) {
            throw new AuthException(AuthException.Reason.EMAIL_TAKEN, "E-mail already registered");
        }
    }

    @Transactional(readOnly = true)
    public Token login(String email, String password) {
        var user = users.findByEmail(normalize(email));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        if (!passwords.matches(password, hash) || user.isEmpty()) {
            throw new AuthException(AuthException.Reason.BAD_CREDENTIALS, "Invalid e-mail or password");
        }
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("carteira")
                .subject(user.get().getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.ttl()))
                .claim("name", user.get().getName())
                .build();
        String token = jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Token(token, props.ttl().toSeconds());
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
