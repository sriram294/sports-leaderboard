package com.org.playboard.controller.auth;

import com.org.playboard.dto.auth.GoogleSignInRequest;
import com.org.playboard.dto.auth.RefreshRequest;
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
    // requirement for just these two operations — they're the only ones
    // that don't need Authorization: Bearer (see SecurityConfig).
    @SecurityRequirements
    @PostMapping("/google")
    public TokenResponse signInWithGoogle(@Valid @RequestBody GoogleSignInRequest request) {
        return authService.signInWithGoogle(request.idToken());
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
