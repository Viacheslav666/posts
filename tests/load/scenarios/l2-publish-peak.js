/**
 * L2 — пик публикаций (каркас QA-2).
 *
 * Целевой профиль (docs/specs/05-qa.md §6):
 *   1 500 000 каналов в БД, 120 000 публикаций на одну минуту (2 000/с),
 *   повтор 3 «ровных часа»; p99 задержки ≤ 60 с; 0 потерь, 0 дублей.
 *
 * TC-PUB-01 на прототипе: backend PipelineLoadIT (10k, nightly).
 * Полный L2 (120k / 2000/с) + сверка эмулятора — QA-7 на staging.
 *
 * Usage:
 *   k6 run tests/load/scenarios/l2-publish-peak.js
 *   k6 run -e PROFILE=full tests/load/scenarios/l2-publish-peak.js
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';

import { apiBaseUrl } from '../lib/config.js';

const profile = __ENV.PROFILE || 'harness';

const harnessOptions = {
  scenarios: {
    l2_harness: {
      executor: 'constant-vus',
      vus: 2,
      duration: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

const fullOptions = {
  scenarios: {
    schedule_burst: {
      executor: 'constant-arrival-rate',
      rate: 2000,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 500,
      maxVUs: 4000,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.001'],
    // Publication lag is measured out-of-band (scheduler → emulator), not raw HTTP.
    http_req_duration: ['p(95)<500'],
  },
};

export const options = profile === 'full' ? fullOptions : harnessOptions;

export function setup() {
  // TODO(QA-7): seed / load tokens, workspaceId, channelIds; reset emulator counters.
  return { api: apiBaseUrl() };
}

export default function (data) {
  group('L2 harness probe', () => {
    const res = http.get(`${data.api}/api/v1/ping`);
    check(res, { 'api alive before publish peak': (r) => r.status === 200 });
  });

  // TODO(QA-5/QA-7):
  // 1) POST /posts + schedule onto the next wall-clock minute
  // 2) await publications via API/WS
  // 3) GET social-emulator /_control/posts and assert 0 losses / 0 duplicates

  sleep(1);
}

export function teardown(_data) {
  // TODO: export Grafana links / emulator diff summary for the load report.
}
