import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config for local development and CI/nightly.
 *
 * Env:
 * - PLAYWRIGHT_BASE_URL — web origin (default http://127.0.0.1:3000)
 * - API_BASE_URL — posts-api origin for request smoke (default http://127.0.0.1:8080)
 * - PLAYWRIGHT_SKIP_WEBSERVER=1 — do not start `next dev` (compose `full` / already running)
 * - CI — enables forbidOnly, retries, list+html+junit reporters
 */
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:3000';
const isCi = Boolean(process.env.CI);

export default defineConfig({
  testDir: './specs',
  fullyParallel: true,
  forbidOnly: isCi,
  retries: isCi ? 1 : 0,
  workers: isCi ? 2 : undefined,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: isCi
    ? [
        ['list'],
        ['html', { open: 'never', outputFolder: 'playwright-report' }],
        ['junit', { outputFile: 'reports/junit.xml' }],
      ]
    : [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  outputDir: 'test-results',
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'mobile-chrome',
      use: { ...devices['Pixel 5'] },
      testMatch: /smoke\.spec\.ts/,
    },
  ],
  webServer: process.env.PLAYWRIGHT_SKIP_WEBSERVER
    ? undefined
    : {
        command: 'pnpm --dir ../../frontend --filter @posts/web dev',
        url: baseURL,
        reuseExistingServer: !isCi,
        timeout: 120_000,
        env: {
          ...process.env,
          NEXT_PUBLIC_API_MOCKING: 'true',
        },
      },
});
