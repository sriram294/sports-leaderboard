package com.org.playboard.service.notification;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.org.playboard.entity.device.DeviceToken;
import com.org.playboard.repository.device.DeviceTokenRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends FCM push notifications to a set of users' devices. Best-effort by design:
 * every failure path is swallowed and logged so a push problem can never surface
 * to (or roll back) the caller that triggered it. When Firebase is unconfigured
 * (see {@code FirebaseConfig}) every send is a no-op.
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    // FCM caps a multicast at 500 tokens per request.
    private static final int MAX_TOKENS_PER_MULTICAST = 500;

    private final Optional<FirebaseMessaging> firebaseMessaging;
    private final DeviceTokenRepository deviceTokenRepository;

    public PushNotificationService(
            Optional<FirebaseMessaging> firebaseMessaging, DeviceTokenRepository deviceTokenRepository) {
        this.firebaseMessaging = firebaseMessaging;
        this.deviceTokenRepository = deviceTokenRepository;
    }

    /**
     * Delivers a notification to every registered device of the given users.
     * Tokens FCM reports as permanently invalid are pruned. Runs in its own
     * transaction (the triggering one has already committed). Returns a
     * {@link PushResult} for diagnostics.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PushResult sendToUsers(Collection<UUID> userIds, String title, String body, Map<String, String> data) {
        if (firebaseMessaging.isEmpty()) {
            log.info("Firebase disabled; skipping push \"{}\" to {} user(s).", title, userIds.size());
            return PushResult.disabled();
        }
        if (userIds.isEmpty()) {
            log.info("Push \"{}\" has no recipients; skipping.", title);
            return PushResult.noTokens();
        }
        List<DeviceToken> tokens = deviceTokenRepository.findByUserIdIn(userIds);
        if (tokens.isEmpty()) {
            log.info("No device tokens for {} recipient user(s); skipping push \"{}\".", userIds.size(), title);
            return PushResult.noTokens();
        }

        List<String> tokenStrings = tokens.stream().map(DeviceToken::getToken).toList();
        log.info("Sending push \"{}\" to {} token(s) across {} user(s).", title, tokenStrings.size(), userIds.size());

        List<String> deadTokens = new ArrayList<>();
        Set<String> errors = new LinkedHashSet<>();
        int sent = 0;
        int failed = 0;
        for (int start = 0; start < tokenStrings.size(); start += MAX_TOKENS_PER_MULTICAST) {
            List<String> batch =
                    tokenStrings.subList(start, Math.min(start + MAX_TOKENS_PER_MULTICAST, tokenStrings.size()));
            try {
                MulticastMessage message = MulticastMessage.builder()
                        .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                        .putAllData(data == null ? Map.of() : data)
                        .addAllTokens(batch)
                        .build();
                BatchResponse response = firebaseMessaging.get().sendEachForMulticast(message);
                sent += response.getSuccessCount();
                failed += response.getFailureCount();
                collectFailures(batch, response, deadTokens, errors);
            } catch (Exception e) {
                // A whole-batch failure (network, auth) is logged, not propagated.
                failed += batch.size();
                errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                log.warn("Failed to send push \"{}\" to {} token(s).", title, batch.size(), e);
            }
        }

        if (!deadTokens.isEmpty()) {
            log.info("Pruning {} stale device token(s).", deadTokens.size());
            deviceTokenRepository.deleteByTokenIn(deadTokens);
        }

        if (failed > 0) {
            log.warn("Push \"{}\": {} sent, {} failed. Errors: {}", title, sent, failed, errors);
        } else {
            log.info("Push \"{}\": {} sent, {} failed.", title, sent, failed);
        }
        return new PushResult(true, tokenStrings.size(), sent, failed, List.copyOf(errors));
    }

    private void collectFailures(
            List<String> batch, BatchResponse response, List<String> deadTokens, Set<String> errors) {
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse sr = responses.get(i);
            if (sr.isSuccessful() || sr.getException() == null) {
                continue;
            }
            MessagingErrorCode code = sr.getException().getMessagingErrorCode();
            errors.add(code == null ? sr.getException().getMessage() : code.name());
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                deadTokens.add(batch.get(i));
            }
        }
    }
}
