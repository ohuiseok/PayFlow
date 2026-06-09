import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  testMatch: /payflow-api-auth\.spec\.ts/,
  timeout: 30000,
  use: {
    ...devices['Desktop Chrome'],
    baseURL: 'http://127.0.0.1:19007',
  },
  webServer: {
    command: 'npx expo start --web --port 19007',
    env: {
      EXPO_PUBLIC_USE_DUMMY_DATA: 'false',
      EXPO_PUBLIC_API_BASE_URL: 'http://127.0.0.1:9999',
    },
    reuseExistingServer: true,
    timeout: 120000,
    url: 'http://127.0.0.1:19007',
  },
});
