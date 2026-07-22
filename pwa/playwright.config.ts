import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  // Block the service worker so route mocks aren't bypassed by SW fetch handling.
  use: { baseURL: 'http://localhost:5173', headless: true, serviceWorkers: 'block' },
  webServer: { command: 'npm run dev -- --host 0.0.0.0', url: 'http://localhost:5173', reuseExistingServer: true },
});
