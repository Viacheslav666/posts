/**
 * k6 smoke — liveness of posts-api (`GET /api/v1/ping`).
 *
 * Usage:
 *   k6 run tests/load/smoke.js
 *   API_BASE_URL=http://127.0.0.1:8080 k6 run tests/load/smoke.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

import { apiBaseUrl, smokeThresholds } from './lib/config.js';

export const options = {
  vus: 1,
  duration: '15s',
  thresholds: smokeThresholds,
};

export default function () {
  const res = http.get(`${apiBaseUrl()}/api/v1/ping`);
  check(res, {
    'ping status 200': (r) => r.status === 200,
    'ping body status ok': (r) => {
      try {
        return r.json('status') === 'ok';
      } catch (_) {
        return false;
      }
    },
  });
  sleep(1);
}
