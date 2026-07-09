package com.org.playboard.common;

import org.springframework.http.HttpStatus;

/**
 * Base for exceptions that should surface as an RFC 7807 {@code ProblemDetail}
 * with a stable {@code code} the Android app can switch on instead of parsing
 * message strings (see api-contracts.md § Conventions).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
