import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  testMatch: /payflow-api-live\.spec\.ts/,
  timeout: 60000,
  use: {
    baseURL: process.env.PAYFLOW_API_BASE_URL ?? 'http://127.0.0.1:8080',
  },
});
