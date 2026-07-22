# Playboard PWA

```bash
npm install
cp .env.example .env.local
npm run dev
```

`VITE_API_URL` points at the API prefix (default `/api/v1`) and
`VITE_GOOGLE_CLIENT_ID` is the web OAuth client ID accepted by the backend.
For production static hosting, configure SPA fallback to `index.html`, serve
over HTTPS, and deploy `manifest.webmanifest` and `sw.js` at the app root.

Verification:

```bash
npm run build
npm test
```
