/**
 * Shared k6 env helpers for Posts load harness (QA-2).
 *
 * Override in CI/nightly/staging via environment variables.
 */

export function apiBaseUrl() {
  return (__ENV.API_BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
}

export function webBaseUrl() {
  return (__ENV.WEB_BASE_URL || 'http://127.0.0.1:3000').replace(/\/$/, '');
}

/** Soft defaults for smoke; L1/L2 scenarios override via their own options. */
export const smokeThresholds = {
  http_req_failed: ['rate<0.01'],
  http_req_duration: ['p(95)<500'],
};
