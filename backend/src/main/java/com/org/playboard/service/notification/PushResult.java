package com.org.playboard.service.notification;

import java.util.List;

/**
 * Outcome of a push send, surfaced for diagnostics (logs + the test endpoint).
 *
 * @param firebaseEnabled whether Firebase was configured at all
 * @param tokens number of device tokens the send targeted
 * @param sent number FCM accepted
 * @param failed number FCM rejected
 * @param errors distinct FCM error codes/messages for the failures (empty on success)
 */
public record PushResult(boolean firebaseEnabled, int tokens, int sent, int failed, List<String> errors) {

    public static PushResult disabled() {
        return new PushResult(false, 0, 0, 0, List.of());
    }

    public static PushResult noTokens() {
        return new PushResult(true, 0, 0, 0, List.of());
    }
}
