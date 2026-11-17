import { expect, type APIRequestContext } from '@playwright/test';

/**
 * Default API base for local compose / bootRun (`make backend`, profile `full`).
 * Override with `API_BASE_URL` in CI and nightly.
 */
export function apiBaseUrl(): string {
  return process.env.API_BASE_URL ?? 'http://127.0.0.1:8080';
}

/**
 * Smoke check for `GET /api/v1/ping` → `{"status":"ok"}`.
 */
export async function expectApiPing(request: APIRequestContext, baseUrl = apiBaseUrl()): Promise<void> {
  const response = await request.get(`${baseUrl.replace(/\/$/, '')}/api/v1/ping`);
  expect(response.status(), await response.text()).toBe(200);
  expect(await response.json()).toEqual({ status: 'ok' });
}
