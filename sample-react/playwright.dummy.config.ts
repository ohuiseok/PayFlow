import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  testMatch: /payflow-(smoke|guard)\.spec\.ts/,
  timeout: 30000,
  use: {
    ...devices['Desktop Chrome'],
    baseURL: 'http://127.0.0.1:19008',
  },
  webServer: {
    command: 'npx expo start --web --port 19008',
    env: {
      EXPO_PUBLIC_USE_DUMMY_DATA: 'true',
    },
    reuseExistingServer: true,
    timeout: 120000,
    url: 'http://127.0.0.1:19008',
  },
});
