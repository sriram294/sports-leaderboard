import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: './',
  server: {
    // Fail loudly if 5173 is taken instead of silently moving to 5174 — otherwise
    // a browser tab left on the stale 5173 server (which has no proxy) 404s /api.
    port: 5173,
    strictPort: true,
    // Google Sign-In popup delivers the credential back via window.postMessage.
    // Without this, the popup's stricter COOP severs the opener link and Chrome
    // drops the message, so the sign-in callback never fires.
    headers: { 'Cross-Origin-Opener-Policy': 'same-origin-allow-popups' },
    proxy: {
      '/api': { target: 'https://playboard-prd.cooperbcknd.in', changeOrigin: true },
    },
  },
});
