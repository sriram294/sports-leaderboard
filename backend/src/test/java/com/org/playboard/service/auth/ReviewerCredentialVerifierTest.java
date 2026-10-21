package com.org.playboard.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.org.playboard.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class ReviewerCredentialVerifierTest {

    // Low cost keeps the suite fast; production hashes use cost 12.
    private static final String HASH = new BCryptPasswordEncoder(4).encode("s3cret-pass");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T10:00:00Z"));

    @Test
    void acceptsMatchingCredentials() {
        ReviewerCredentialVerifier verifier = new ReviewerCredentialVerifier("reviewer", HASH, clock);

        assertThat(verifier.isEnabled()).isTrue();
        assertThatCode(() -> verifier.verify("reviewer", "s3cret-pass")).doesNotThrowAnyException();
        assertThatCode(() -> verifier.verify(" reviewer ", "s3cret-pass")).doesNotThrowAnyException();
    }

    @Test
    void wrongUsernameAndWrongPasswordFailIdentically() {
        ReviewerCredentialVerifier verifier = new ReviewerCredentialVerifier("reviewer", HASH, clock);

        assertInvalid(verifier, "someone", "s3cret-pass");
        assertInvalid(verifier, "reviewer", "wrong");
    }

    @Test
    void blankOrMalformedConfigDisablesLogin() {
        ReviewerCredentialVerifier blank = new ReviewerCredentialVerifier("", "", clock);
        ReviewerCredentialVerifier noHash = new ReviewerCredentialVerifier("reviewer", " ", clock);
        ReviewerCredentialVerifier plaintext = new ReviewerCredentialVerifier("reviewer", "s3cret-pass", clock);

        for (ReviewerCredentialVerifier verifier : new ReviewerCredentialVerifier[] {blank, noHash, plaintext}) {
            assertThat(verifier.isEnabled()).isFalse();
            assertInvalid(verifier, "reviewer", "s3cret-pass");
        }
    }

    @Test
    void throttlesAfterRepeatedFailuresUntilWindowSlides() {
        ReviewerCredentialVerifier verifier = new ReviewerCredentialVerifier("reviewer", HASH, clock);
        for (int i = 0; i < ReviewerCredentialVerifier.MAX_FAILURES; i++) {
            assertInvalid(verifier, "reviewer", "guess-" + i);
        }

        // Even the right password is refused while throttled.
        assertThatThrownBy(() -> verifier.verify("reviewer", "s3cret-pass"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getCode()).isEqualTo("REVIEWER_LOGIN_RATE_LIMITED");
                });

        clock.advance(ReviewerCredentialVerifier.FAILURE_WINDOW.plusSeconds(1));
        assertThatCode(() -> verifier.verify("reviewer", "s3cret-pass")).doesNotThrowAnyException();
    }

    private static void assertInvalid(ReviewerCredentialVerifier verifier, String username, String password) {
        assertThatThrownBy(() -> verifier.verify(username, password))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getCode()).isEqualTo("REVIEWER_CREDENTIALS_INVALID");
                    assertThat(e.getMessage()).isEqualTo("Invalid username or password");
                });
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
