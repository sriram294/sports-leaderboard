import { api } from './data';
import { pushConfigFrom, type PushConfig } from './push-core';

/**
 * Web push via **Firebase Cloud Messaging for Web** — the browser analog of Android's
 * FCM device registration. The backend send path is unchanged: it delivers to the web
 * FCM token exactly as it does to Android tokens. Everything here is best-effort and
 * non-blocking — an unsupported browser, missing config, denied permission, or a
 * Firebase error all resolve to a benign result rather than throwing.
 *
 * Config comes from Vite env (`VITE_FIREBASE_*` + `VITE_FCM_VAPID_KEY`) or a runtime
 * `window.__PLAYBOARD_PUSH_CONFIG__` override; until it's set, push is simply "unconfigured".
 */
const PUSH_TOKEN_KEY = 'playboard.push.token';

type PushWindow = Window & {
  __PLAYBOARD_PUSH_CONFIG__?: Record<string, string | undefined>;
  /** Test/manual seam: bypass the Firebase SDK and use this token directly. */
  __PLAYBOARD_FCM_TOKEN__?: string;
};

function resolveConfig(): PushConfig | null {
  const override = (window as PushWindow).__PLAYBOARD_PUSH_CONFIG__;
  return pushConfigFrom(override ?? (import.meta.env as unknown as Record<string, string | undefined>));
}

/** Whether the browser exposes the APIs web push needs at all. */
export function isPushSupported(): boolean {
  return typeof navigator !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window && typeof Notification !== 'undefined';
}

/** Whether a Firebase Web + VAPID config is present (else the UI hides the control). */
export const isPushConfigured = (): boolean => resolveConfig() !== null;

/** Whether this browser currently holds a registered push token. */
export const isPushEnabled = (): boolean => Boolean(localStorage.getItem(PUSH_TOKEN_KEY));

/** Obtains an FCM registration token — via the test seam if present, else the Firebase SDK. */
async function fetchFcmToken(config: PushConfig): Promise<string> {
  const seam = (window as PushWindow).__PLAYBOARD_FCM_TOKEN__;
  if (typeof seam === 'string') return seam;

  const { initializeApp, getApps, getApp } = await import('firebase/app');
  const { getMessaging, getToken } = await import('firebase/messaging');
  const app = getApps().length
    ? getApp()
    : initializeApp({
        apiKey: config.apiKey,
        authDomain: config.authDomain,
        projectId: config.projectId,
        messagingSenderId: config.messagingSenderId,
        appId: config.appId,
      });
  const registration = await navigator.serviceWorker.ready;
  return getToken(getMessaging(app), { vapidKey: config.vapidKey, serviceWorkerRegistration: registration });
}

export type PushEnableResult = 'enabled' | 'denied' | 'unsupported' | 'unconfigured' | 'error';

/**
 * Requests notification permission (must be called from a user gesture), registers an
 * FCM token, and records it at `/devices` with `platform: "web"`. Idempotent on the token.
 */
export async function enablePush(): Promise<PushEnableResult> {
  if (!isPushSupported()) return 'unsupported';
  const config = resolveConfig();
  if (!config) return 'unconfigured';

  let permission = Notification.permission;
  if (permission === 'default') permission = await Notification.requestPermission();
  if (permission !== 'granted') return 'denied';

  try {
    const token = await fetchFcmToken(config);
    if (!token) return 'error';
    await api.registerDevice(token, 'web');
    localStorage.setItem(PUSH_TOKEN_KEY, token);
    return 'enabled';
  } catch {
    return 'error';
  }
}

/**
 * Removes this browser's push registration — deletes the FCM token and unregisters it
 * server-side. Safe to call unconditionally (e.g. on sign-out); a no-op with no token.
 */
export async function disablePush(): Promise<void> {
  const token = localStorage.getItem(PUSH_TOKEN_KEY);
  localStorage.removeItem(PUSH_TOKEN_KEY);
  if (!token) return;

  const usingRealSdk = typeof (window as PushWindow).__PLAYBOARD_FCM_TOKEN__ !== 'string';
  if (usingRealSdk && isPushSupported() && isPushConfigured()) {
    try {
      const { getApps, getApp } = await import('firebase/app');
      const { getMessaging, deleteToken } = await import('firebase/messaging');
      if (getApps().length) await deleteToken(getMessaging(getApp())).catch(() => undefined);
    } catch {
      // Best-effort; we still unregister server-side below.
    }
  }
  await api.unregisterDevice(token).catch(() => undefined);
}
