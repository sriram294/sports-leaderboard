package com.org.playboard.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class FirebaseConfigTest {

    private final FirebaseConfig config = new FirebaseConfig();

    @Test
    void disabledWhenCredentialsBlank() {
        assertThat(config.firebaseMessaging("", 5000, 10000)).isNull();
    }

    /**
     * Guards the firebase-admin dependency exclusions in pom.xml (Cloud Storage / Firestore,
     * dropped to keep the native-image build small): initializing the app and the messaging
     * client from a well-formed key must not need anything we excluded.
     */
    @Test
    void initializesMessagingFromServiceAccountKey() throws Exception {
        assertThat(config.firebaseMessaging(fakeServiceAccountBase64(), 5000, 10000)).isNotNull();
    }

    /** A syntactically valid service-account key with a throwaway RSA key; never sent anywhere. */
    static String fakeServiceAccountBase64() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\\n"
                + Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\\n-----END PRIVATE KEY-----\\n";
        String json = """
                {"type":"service_account","project_id":"playboard-test","private_key_id":"test",\
                "private_key":"%s","client_email":"test@playboard-test.iam.gserviceaccount.com",\
                "client_id":"1","token_uri":"https://oauth2.googleapis.com/token"}"""
                .formatted(pem);
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
