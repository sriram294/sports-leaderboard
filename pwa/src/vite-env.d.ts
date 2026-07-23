/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL?: string;
  // Firebase Web app config + FCM Web Push VAPID key (all public). Set in Vercel to
  // enable web push; absent = push stays "unconfigured" and the UI hides the control.
  readonly VITE_FIREBASE_API_KEY?: string;
  readonly VITE_FIREBASE_AUTH_DOMAIN?: string;
  readonly VITE_FIREBASE_PROJECT_ID?: string;
  readonly VITE_FIREBASE_MESSAGING_SENDER_ID?: string;
  readonly VITE_FIREBASE_APP_ID?: string;
  readonly VITE_FCM_VAPID_KEY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
