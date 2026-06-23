import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  testMatch: /capture-screenshots\.spec\.ts/,
  timeout: 60000,
  workers: 1,
  use: {
    ...devices['Desktop Chrome'],
    baseURL: 'http://127.0.0.1:19006',
    headless: false,
  },
  // Use already-running Expo server
});
