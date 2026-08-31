package com.org.playboard.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.org.playboard.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppleTokenVerifierTest {

    private static final Instant NOW = Instant.parse("2026-08-31T10:00:00Z");
    private static final String CLIENT_ID = "com.org.playboard";
    private static final String KEY_ID = "test-key";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private KeyPair keyPair;
    private AppleTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        verifier = new AppleTokenVerifier(
                List.of(CLIENT_ID),
                () -> jwks((RSAPublicKey) keyPair.getPublic()),
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void verifiesSignatureIssuerAudienceExpiryAndIdentityClaims() throws Exception {
        String token = token(Map.of(
                "iss", "https://appleid.apple.com",
                "aud", CLIENT_ID,
                "sub", "apple-user-123",
                "email", "person@privaterelay.appleid.com",
                "exp", NOW.plusSeconds(300).getEpochSecond()));

        VerifiedIdentity identity = verifier.verify(token, "Priya", "Shah");

        assertThat(identity.subject()).isEqualTo("apple-user-123");
        assertThat(identity.email()).isEqualTo("person@privaterelay.appleid.com");
        assertThat(identity.displayName()).isEqualTo("Priya Shah");
    }

    @Test
    void rejectsAnUnexpectedAudience() throws Exception {
        String token = token(Map.of(
                "iss", "https://appleid.apple.com",
                "aud", "com.example.other",
                "sub", "apple-user-123",
                "exp", NOW.plusSeconds(300).getEpochSecond()));

        assertInvalid(token);
    }

    @Test
    void rejectsAnExpiredToken() throws Exception {
        String token = token(Map.of(
                "iss", "https://appleid.apple.com",
                "aud", CLIENT_ID,
                "sub", "apple-user-123",
                "exp", NOW.minusSeconds(1).getEpochSecond()));

        assertInvalid(token);
    }

    @Test
    void missingConfigurationFailsSafelyWithoutLoadingKeys() {
        AppleTokenVerifier unconfigured = new AppleTokenVerifier(
                List.of(),
                () -> {
                    throw new AssertionError("Keys must not be loaded");
                },
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> unconfigured.verify("token", null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("APPLE_AUTH_NOT_CONFIGURED"));
    }

    private String token(Map<String, Object> claims) throws Exception {
        String header = encode(objectMapper.writeValueAsBytes(Map.of("alg", "RS256", "kid", KEY_ID)));
        String payload = encode(objectMapper.writeValueAsBytes(claims));
        String signingInput = header + "." + payload;
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + encode(signer.sign());
    }

    private String jwks(RSAPublicKey key) throws Exception {
        return objectMapper.writeValueAsString(Map.of("keys", List.of(Map.of(
                "kid", KEY_ID,
                "kty", "RSA",
                "alg", "RS256",
                "n", encode(unsigned(key.getModulus().toByteArray())),
                "e", encode(unsigned(key.getPublicExponent().toByteArray()))))));
    }

    private static byte[] unsigned(byte[] value) {
        return value.length > 1 && value[0] == 0 ? java.util.Arrays.copyOfRange(value, 1, value.length) : value;
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> verifier.verify(token, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("APPLE_TOKEN_INVALID"));
    }
}
