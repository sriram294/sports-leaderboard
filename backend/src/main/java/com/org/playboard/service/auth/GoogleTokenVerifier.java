package com.org.playboard.service.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.org.playboard.common.ApiException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Verifies the Google ID token Android hands us after Credential Manager
 * sign-in. {@code serverClientId} on the Android side must be the same
 * Web-application OAuth Client ID configured here, or the token's `aud`
 * check fails (see api-contracts.md § Google Sign-In flow).
 */
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${playboard.auth.google-client-id}") String googleClientId) {
        this.verifier =
                new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(Collections.singletonList(googleClientId))
                        .build();
    }

    public GoogleIdToken.Payload verify(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw invalid();
            }
            return idToken.getPayload();
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw invalid();
        }
    }

    private ApiException invalid() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED, "GOOGLE_TOKEN_INVALID", "Google ID token failed verification");
    }
}
