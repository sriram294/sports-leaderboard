# Playboard PWA

```bash
npm install
cp .env.example .env.local
npm run dev
```

`VITE_API_URL` points at the API prefix (default `/api/v1`) and
`VITE_GOOGLE_CLIENT_ID` is the web OAuth client ID accepted by the backend.
Web push is enabled only when all `VITE_FIREBASE_*` values and
`VITE_FCM_VAPID_KEY` from `.env.example` are set. These are Firebase Web app
configuration values, not server credentials.
For the Vercel deployment, set `VITE_API_URL` to
`https://playboard-prd.cooperbcknd.in/api/v1` and redeploy, because Vite embeds
these variables during the build.
For production static hosting, configure SPA fallback to `index.html`, serve
over HTTPS, and deploy `manifest.webmanifest` and `sw.js` at the app root.
The service worker precaches the built shell during installation; after
`navigator.serviceWorker.ready` resolves, the app can be reloaded offline while
API calls remain network-only.

Verification:

```bash
npm run build
npm test
npm run test:e2e
```
