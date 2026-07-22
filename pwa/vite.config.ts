import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: './',
  server: {
    // Google Sign-In popup delivers the credential back via window.postMessage.
    // Without this, the popup's stricter COOP severs the opener link and Chrome
    // drops the message, so the sign-in callback never fires.
    headers: { 'Cross-Origin-Opener-Policy': 'same-origin-allow-popups' },
    proxy: {
      '/api': { target: 'https://playboard-prd.cooperbcknd.in', changeOrigin: true },
    },
  },
});
