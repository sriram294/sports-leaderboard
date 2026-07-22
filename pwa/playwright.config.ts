import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  use: { baseURL: 'http://localhost:5173', headless: true },
  webServer: { command: 'npm run dev -- --host 0.0.0.0', url: 'http://localhost:5173', reuseExistingServer: true },
});
