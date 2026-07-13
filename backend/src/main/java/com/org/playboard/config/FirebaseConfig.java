package com.org.playboard.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

/**
 * Initializes the Firebase Admin SDK from a base64-encoded service-account key
 * ({@code playboard.firebase.credentials-base64}).
 *
 * <p>Firebase is <b>optional at runtime</b>: local dev and {@code @SpringBootTest}
 * integration tests run without credentials. When the key is blank (or fails to
 * parse), this returns a {@code null} {@link FirebaseMessaging} bean, and consumers
 * inject it via {@code ObjectProvider} so push notifications become a logged no-op
 * (see {@code PushNotificationService}) rather than preventing the app from booting.
 *
 * <p>Note: the bean type is {@code FirebaseMessaging} (nullable), <b>not</b>
 * {@code Optional<FirebaseMessaging>} — Spring special-cases {@code Optional<T>}
 * injection points to resolve the inner type {@code T}, so an {@code Optional} bean
 * is never actually injected and consumers would always see {@code Optional.empty()}.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);
    private static final String APP_NAME = "playboard";

    @Bean
    @Nullable
    public FirebaseMessaging firebaseMessaging(
            @Value("${playboard.firebase.credentials-base64:}") String credentialsBase64) {
        if (credentialsBase64 == null || credentialsBase64.isBlank()) {
            log.info("Firebase not configured (playboard.firebase.credentials-base64 is blank); "
                    + "push notifications are disabled.");
            return null;
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
            return FirebaseMessaging.getInstance(app);
        } catch (Exception e) {
            // Never fail startup over a bad key — degrade to no-op push instead.
            log.error("Failed to initialize Firebase from credentials-base64; "
                    + "push notifications disabled. Check FIREBASE_CREDENTIALS_BASE64.",
                    e);
            return null;
        }
    }
}
