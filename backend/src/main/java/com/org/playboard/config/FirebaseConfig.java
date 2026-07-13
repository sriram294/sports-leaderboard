package com.org.playboard.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Initializes the Firebase Admin SDK from a base64-encoded service-account key
 * ({@code playboard.firebase.credentials-base64}).
 *
 * <p>Firebase is <b>optional at runtime</b>: local dev and {@code @SpringBootTest}
 * integration tests run without credentials. When the key is blank (or fails to
 * parse), this exposes an empty {@code Optional<FirebaseMessaging>} and push
 * notifications become a logged no-op (see {@code PushNotificationService}) rather
 * than preventing the app from booting.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);
    private static final String APP_NAME = "playboard";

    @Bean
    public Optional<FirebaseMessaging> firebaseMessaging(
            @Value("${playboard.firebase.credentials-base64:}") String credentialsBase64) {
        if (credentialsBase64 == null || credentialsBase64.isBlank()) {
            log.info("Firebase not configured (playboard.firebase.credentials-base64 is blank); "
                    + "push notifications are disabled.");
            return Optional.empty();
        }
        try {
            byte[] json = Base64.getDecoder().decode(credentialsBase64.trim());
            GoogleCredentials credentials =
                    GoogleCredentials.fromStream(new ByteArrayInputStream(json));
            FirebaseOptions options =
                    FirebaseOptions.builder().setCredentials(credentials).build();
            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(a -> APP_NAME.equals(a.getName()))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(options, APP_NAME));
            log.info("Firebase initialized; push notifications enabled.");
            return Optional.of(FirebaseMessaging.getInstance(app));
        } catch (Exception e) {
            // Never fail startup over a bad key — degrade to no-op push instead.
            log.error("Failed to initialize Firebase from credentials-base64; "
                    + "push notifications disabled. Check FIREBASE_CREDENTIALS_BASE64.",
                    e);
            return Optional.empty();
        }
    }
}
