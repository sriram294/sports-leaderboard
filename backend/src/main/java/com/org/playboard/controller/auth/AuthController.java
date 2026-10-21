package com.org.playboard.controller.auth;

import com.org.playboard.dto.auth.AppleSignInRequest;
import com.org.playboard.dto.auth.GoogleSignInRequest;
import com.org.playboard.dto.auth.RefreshRequest;
import com.org.playboard.dto.auth.ReviewerSignInRequest;
import com.org.playboard.dto.auth.TokenResponse;
import com.org.playboard.service.auth.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Empty @SecurityRequirements overrides OpenApiConfig's global bearer
    // requirement for these public authentication operations — they're the only ones
    // that don't need Authorization: Bearer (see SecurityConfig).
    @SecurityRequirements
    @PostMapping("/google")
    public TokenResponse signInWithGoogle(@Valid @RequestBody GoogleSignInRequest request) {
        return authService.signInWithGoogle(request.idToken());
    }

    @SecurityRequirements
    @PostMapping("/apple")
    public TokenResponse signInWithApple(@Valid @RequestBody AppleSignInRequest request) {
        return authService.signInWithApple(
                request.identityToken(), request.givenName(), request.familyName());
    }

    // Hidden store-review login (long-press the logo on the Android login screen).
    // Disabled unless REVIEWER_USERNAME + REVIEWER_PASSWORD_HASH are configured.
    @SecurityRequirements
    @PostMapping("/reviewer")
    public TokenResponse signInAsReviewer(@Valid @RequestBody ReviewerSignInRequest request) {
        return authService.signInAsReviewer(request.username(), request.password());
    }

    @SecurityRequirements
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
