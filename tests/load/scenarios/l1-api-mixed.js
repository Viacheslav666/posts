/**
 * L1 — смешанная нагрузка API (каркас QA-2).
 *
 * Целевой профиль (docs/specs/05-qa.md §6):
 *   10 000 VU, 5 000 RPS; 60 % чтение / 20 % запись постов / 10 % media / 10 % прочее; 30 мин
 *   p95 чтение ≤ 300 мс, запись ≤ 500 мс, ошибки < 0.1 %
 *
 * Сейчас: урезанный smoke-профиль + TODO-хуки под реальные эндпоинты (после FE/BE auth).
 *
 * Usage:
 *   k6 run tests/load/scenarios/l1-api-mixed.js
 *   k6 run -e PROFILE=full tests/load/scenarios/l1-api-mixed.js   # when staging ready
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { SharedArray } from 'k6/data';

import { apiBaseUrl } from '../lib/config.js';

const profile = __ENV.PROFILE || 'harness';

const harnessOptions = {
  scenarios: {
    l1_harness: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 5 },
        { duration: '40s', target: 5 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
    'http_req_duration{name:ping}': ['p(95)<300'],
  },
};

/** Full L1 — enable on staging with seed + auth tokens (QA-7). */
const fullOptions = {
  scenarios: {
    l1_full: {
      executor: 'constant-arrival-rate',
      rate: 5000,
      timeUnit: '1s',
      duration: '30m',
      preAllocatedVUs: 2000,
      maxVUs: 10000,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.001'],
    'http_req_duration{name:read}': ['p(95)<300'],
    'http_req_duration{name:write}': ['p(95)<500'],
  },
};

export const options = profile === 'full' ? fullOptions : harnessOptions;

// Placeholder for future auth/workspace fixtures (QA-7).
const _placeholders = new SharedArray('l1-placeholders', () => [
  { note: 'Replace with seeded workspace/channel ids when L1 is wired' },
]);

export default function () {
  const base = apiBaseUrl();

  group('L1 read mix (harness: ping stands in for calendar/list)', () => {
    const res = http.get(`${base}/api/v1/ping`, { tags: { name: 'ping' } });
    check(res, { 'read-like 200': (r) => r.status === 200 });
  });

  // TODO(QA-7): 60% GET /calendar, GET /posts; 20% POST/PATCH posts;
  // 10% media presign; 10% me/notifications — with JWT from setup().
  void _placeholders;

  sleep(0.5);
}
