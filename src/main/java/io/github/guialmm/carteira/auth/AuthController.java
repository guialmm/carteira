package io.github.guialmm.carteira.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação")
class AuthController {

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    record RegisterRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    record RegisterResponse(UUID userId, UUID accountId, String name, String email) {
    }

    record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria um usuário e a conta dele, com saldo zero")
    RegisterResponse register(@Valid @RequestBody RegisterRequest body) {
        var r = auth.register(body.name(), body.email(), body.password());
        return new RegisterResponse(r.user().getId(), r.account().getId(), r.user().getName(), r.user().getEmail());
    }

    @PostMapping("/login")
    @Operation(summary = "Troca e-mail e senha por um token JWT")
    AuthService.Token login(@Valid @RequestBody LoginRequest body) {
        return auth.login(body.email(), body.password());
    }
}
