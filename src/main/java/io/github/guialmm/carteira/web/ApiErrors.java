package io.github.guialmm.carteira.web;

import io.github.guialmm.carteira.auth.AuthException;
import io.github.guialmm.carteira.pix.PixKeyException;
import io.github.guialmm.carteira.transfer.TransferException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Domain errors as RFC 9457 problem details, with a stable {@code code} clients can switch on. */
@RestControllerAdvice
class ApiErrors {

    @ExceptionHandler
    ProblemDetail transfer(TransferException e) {
        HttpStatus status = switch (e.getReason()) {
            case ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case IDEMPOTENCY_KEY_REUSED -> HttpStatus.CONFLICT;
            case SAME_ACCOUNT, INVALID_AMOUNT, INSUFFICIENT_FUNDS -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return problem(status, e.getReason().name(), e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail auth(AuthException e) {
        HttpStatus status = switch (e.getReason()) {
            case EMAIL_TAKEN -> HttpStatus.CONFLICT;
            case BAD_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
        };
        return problem(status, e.getReason().name(), e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail pix(PixKeyException e) {
        HttpStatus status = switch (e.getReason()) {
            case INVALID_KEY -> HttpStatus.BAD_REQUEST;
            case KEY_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case KEY_TAKEN -> HttpStatus.CONFLICT;
            case KEY_LIMIT_REACHED, EMAIL_NOT_YOURS -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return problem(status, e.getReason().name(), e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setProperty("code", code);
        return p;
    }
}
