/**
 * Pure, side-effect-free push helpers — unit-testable without a DOM. The stateful
 * browser plumbing (Firebase SDK, Notification permission, localStorage) lives in
 * `push.ts` and builds on these.
 */

/** Resolved Firebase Web + FCM config. `vapidKey` is the Cloud Messaging "Web Push certificate". */
export type PushConfig = {
  apiKey: string;
  authDomain: string;
  projectId: string;
  messagingSenderId: string;
  appId: string;
  vapidKey: string;
};

/**
 * Builds a {@link PushConfig} from a flat source (Vite env or a runtime override),
 * or `null` when the essentials are missing — the signal that push isn't set up yet.
 */
export function pushConfigFrom(source: Record<string, string | undefined>): PushConfig | null {
  const cfg = {
    apiKey: source.VITE_FIREBASE_API_KEY ?? '',
    authDomain: source.VITE_FIREBASE_AUTH_DOMAIN ?? '',
    projectId: source.VITE_FIREBASE_PROJECT_ID ?? '',
    messagingSenderId: source.VITE_FIREBASE_MESSAGING_SENDER_ID ?? '',
    appId: source.VITE_FIREBASE_APP_ID ?? '',
    vapidKey: source.VITE_FCM_VAPID_KEY ?? '',
  };
  return cfg.apiKey && cfg.appId && cfg.messagingSenderId && cfg.vapidKey ? cfg : null;
}

/** The FCM payload shape a web push may carry (either notification and/or data blocks). */
export type PushPayload = { notification?: { title?: string; body?: string }; data?: Record<string, string> };

/** A notification the service worker should display, derived from an FCM push payload. */
export type PushNotification = { title: string; body: string; data: Record<string, string> };

/**
 * Normalizes an FCM web-push payload into what the SW shows. Falls back to the app
 * name for a missing title, and routes a tap to the match/board deep link the
 * payload names (mirrors Android's tap-to-open behavior).
 */
export function pushNotificationFrom(payload: PushPayload | null | undefined): PushNotification {
  const notification = payload?.notification ?? {};
  const data = payload?.data ?? {};
  const title = notification.title || data.title || 'Playboard';
  const body = notification.body || data.body || '';
  const url = data.url || (data.matchId ? '/matches' : data.groupId ? '/board' : '/');
  return { title, body, data: { ...data, url } };
}
