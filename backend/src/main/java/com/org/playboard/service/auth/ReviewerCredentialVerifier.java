package com.org.playboard.service.auth;

import com.org.playboard.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Checks the hidden store-review login ({@code POST /auth/reviewer}) against a single
 * credential configured through {@code REVIEWER_USERNAME} + {@code REVIEWER_PASSWORD_HASH}
 * (a BCrypt hash — the plaintext never reaches the server's config). The login is
 * disabled when either value is blank or the hash is malformed.
 *
 * <p>Every failure — disabled, wrong username, wrong password — returns the same
 * {@code 401 REVIEWER_CREDENTIALS_INVALID}, and a BCrypt check runs on every attempt so
 * response timing doesn't reveal which part was wrong. Failed attempts are throttled
 * globally (there is only one account to guess at): after {@link #MAX_FAILURES} inside
 * {@link #FAILURE_WINDOW} the endpoint answers {@code 429} until the window slides.
 */
@Component
public class ReviewerCredentialVerifier {

    static final int MAX_FAILURES = 10;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(ReviewerCredentialVerifier.class);

    private final byte[] usernameDigest;
    private final String passwordHash;
    // Compared against when the login is disabled, so that path costs the same BCrypt
    // work as a real check. Hash of a random string at the configured cost.
    private final String dummyHash;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final Clock clock;
    private final Deque<Instant> recentFailures = new ArrayDeque<>();

    @Autowired
    public ReviewerCredentialVerifier(
            @Value("${playboard.auth.reviewer.username:}") String username,
            @Value("${playboard.auth.reviewer.password-hash:}") String passwordHash) {
        this(username, passwordHash, Clock.systemUTC());
    }

    ReviewerCredentialVerifier(String username, String passwordHash, Clock clock) {
        this.clock = clock;
        String trimmedUser = username == null ? "" : username.trim();
        String trimmedHash = passwordHash == null ? "" : passwordHash.trim();
        if (trimmedUser.isEmpty() || trimmedHash.isEmpty()) {
            this.usernameDigest = null;
            this.passwordHash = null;
        } else if (!trimmedHash.matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$")) {
            log.error("REVIEWER_PASSWORD_HASH is not a BCrypt hash — reviewer login disabled");
            this.usernameDigest = null;
            this.passwordHash = null;
        } else {
            this.usernameDigest = sha256(trimmedUser);
            this.passwordHash = trimmedHash;
            log.info("Reviewer login enabled");
        }
        int cost = this.passwordHash == null ? 10 : Integer.parseInt(this.passwordHash.substring(4, 6));
        this.dummyHash = new BCryptPasswordEncoder(cost).encode(UUID.randomUUID().toString());
    }

    /** Whether a reviewer credential is configured. */
    public boolean isEnabled() {
        return passwordHash != null;
    }

    /**
     * Returns normally when both the username and password match; otherwise throws {@code 401 REVIEWER_CREDENTIALS_INVALID}, or {@code 429} while
     * failed attempts are throttled.
     */
    public void verify(String username, String password) {
        Instant now = clock.instant();
        synchronized (recentFailures) {
            prune(now);
            if (recentFailures.size() >= MAX_FAILURES) {
                throw new ApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "REVIEWER_LOGIN_RATE_LIMITED",
                        "Too many failed attempts. Try again later.");
            }
        }

        // Evaluate both checks unconditionally (no short-circuit) so timing is uniform.
        boolean usernameMatches = isEnabled()
                && MessageDigest.isEqual(usernameDigest, sha256(username == null ? "" : username.trim()));
        boolean passwordMatches = encoder.matches(
                password == null ? "" : password, isEnabled() ? passwordHash : dummyHash);

        if (isEnabled() && usernameMatches && passwordMatches) {
            return;
        }
        synchronized (recentFailures) {
            recentFailures.addLast(now);
        }
        throw new ApiException(
                HttpStatus.UNAUTHORIZED, "REVIEWER_CREDENTIALS_INVALID", "Invalid username or password");
    }

    private void prune(Instant now) {
        Instant cutoff = now.minus(FAILURE_WINDOW);
        while (!recentFailures.isEmpty() && !recentFailures.peekFirst().isAfter(cutoff)) {
            recentFailures.removeFirst();
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
