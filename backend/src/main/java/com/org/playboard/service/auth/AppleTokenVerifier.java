package com.org.playboard.service.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.org.playboard.common.ApiException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Verifies native Apple identity JWTs against Apple's published signing keys. */
@Component
public class AppleTokenVerifier {

    private static final URI APPLE_KEYS = URI.create("https://appleid.apple.com/auth/keys");
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final List<String> clientIds;
    private final KeySetLoader keySetLoader;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AppleTokenVerifier(@Value("${playboard.auth.apple-client-ids:}") String configuredClientIds) {
        this(
                configuredClientIds.lines()
                        .flatMap(line -> List.of(line.split(",")).stream())
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList(),
                AppleTokenVerifier::loadAppleKeys,
                new ObjectMapper(),
                Clock.systemUTC());
    }

    AppleTokenVerifier(
            List<String> clientIds, KeySetLoader keySetLoader, ObjectMapper objectMapper, Clock clock) {
        this.clientIds = List.copyOf(clientIds);
        this.keySetLoader = keySetLoader;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Returns verified provider claims or a stable unauthorized/configuration error. */
    public VerifiedIdentity verify(String identityToken, String givenName, String familyName) {
        if (clientIds.isEmpty()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "APPLE_AUTH_NOT_CONFIGURED",
                    "Sign in with Apple is not configured");
        }

        try {
            String[] parts = identityToken.split("\\.");
            if (parts.length != 3) {
                throw invalid();
            }

            Map<String, Object> header = decode(parts[0]);
            Map<String, Object> claims = decode(parts[1]);
            if (!"RS256".equals(header.get("alg"))) {
                throw invalid();
            }

            String keyId = requiredString(header, "kid");
            RSAPublicKey publicKey = fetchKey(keyId);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                throw invalid();
            }

            String issuer = requiredString(claims, "iss");
            String subject = requiredString(claims, "sub");
            long expiresAt = requiredNumber(claims, "exp").longValue();
            if (!APPLE_ISSUER.equals(issuer)
                    || !hasAcceptedAudience(claims.get("aud"))
                    || !Instant.ofEpochSecond(expiresAt).isAfter(clock.instant())) {
                throw invalid();
            }

            String email = optionalString(claims.get("email"));
            return new VerifiedIdentity(subject, email, displayName(givenName, familyName));
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private RSAPublicKey fetchKey(String keyId) throws Exception {
        Map<String, Object> document = objectMapper.readValue(keySetLoader.load(), new TypeReference<>() {});
        Object rawKeys = document.get("keys");
        if (!(rawKeys instanceof List<?> keys)) {
            throw invalid();
        }
        for (Object rawKey : keys) {
            if (rawKey instanceof Map<?, ?> key && keyId.equals(key.get("kid"))) {
                BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(requiredMapString(key, "n")));
                BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(requiredMapString(key, "e")));
                return (RSAPublicKey) KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(modulus, exponent));
            }
        }
        throw invalid();
    }

    private Map<String, Object> decode(String encoded) throws Exception {
        byte[] json = Base64.getUrlDecoder().decode(encoded);
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    private boolean hasAcceptedAudience(Object audience) {
        if (audience instanceof String value) {
            return clientIds.contains(value);
        }
        if (audience instanceof List<?> values) {
            return values.stream().anyMatch(clientIds::contains);
        }
        return false;
    }

    private static String displayName(String givenName, String familyName) {
        return List.of(givenName == null ? "" : givenName.trim(), familyName == null ? "" : familyName.trim())
                .stream()
                .filter(value -> !value.isEmpty())
                .reduce((left, right) -> left + " " + right)
                .orElse(null);
    }

    private static String requiredString(Map<String, Object> values, String key) {
        String value = optionalString(values.get(key));
        if (value == null) {
            throw invalid();
        }
        return value;
    }

    private static Number requiredNumber(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) {
            throw invalid();
        }
        return number;
    }

    private static String requiredMapString(Map<?, ?> values, String key) {
        String value = optionalString(values.get(key));
        if (value == null) {
            throw invalid();
        }
        return value;
    }

    private static String optionalString(Object value) {
        return value instanceof String string && !string.isBlank() ? string : null;
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "APPLE_TOKEN_INVALID", "Apple identity token failed verification");
    }

    private static String loadAppleKeys() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(APPLE_KEYS)
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw invalid();
        }
        return response.body();
    }

    @FunctionalInterface
    interface KeySetLoader {
        String load() throws Exception;
    }
}
