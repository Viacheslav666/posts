import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { apiBaseUrl } from '../lib/config.js';

/**
 * QA-14 harness: synthetic antispam inbound events (comments) against API hooks.
 * PROFILE=full targets ~3000 events/s; default smoke uses 30/s for nightly compose.
 */
const lost = new Counter('antispam_hook_lost');

export const options = {
  scenarios: {
    antispam: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.ANTISPAM_RATE || (__ENV.PROFILE === 'full' ? 3000 : 30)),
      timeUnit: '1s',
      duration: __ENV.ANTISPAM_DURATION || '20s',
      preAllocatedVUs: Number(__ENV.PROFILE === 'full' ? 400 : 10),
      maxVUs: Number(__ENV.PROFILE === 'full' ? 2000 : 50),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    'http_req_duration{scenario:antispam}': [
      `p(95)<${Number(__ENV.ANTISPAM_P95_MS || (__ENV.PROFILE === 'full' ? 200 : 500))}`,
      `p(99)<${Number(__ENV.ANTISPAM_P99_MS || (__ENV.PROFILE === 'full' ? 500 : 2000))}`,
    ],
    antispam_hook_lost: ['count==0'],
  },
};

const channelId = __ENV.CHANNEL_ID;
const secret = __ENV.VK_SECRET || '';

export default function () {
  if (!channelId) {
    lost.add(1);
    return;
  }
  const eventId = `as-${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
    type: 'wall_reply_new',
    event_id: eventId,
    group_id: __ENV.GROUP_ID || '1',
    secret,
    object: {
      id: __ITER,
      post_id: 10,
      from_id: 9000 + __VU,
      text: 'spam load comment',
    },
  });
  const res = http.post(`${apiBaseUrl()}/hooks/vk/${channelId}`, payload, {
    headers: { 'Content-Type': 'application/json' },
    timeout: '2s',
  });
  const ok = check(res, {
    'status 200': (r) => r.status === 200,
    'body ok': (r) => String(r.body).trim() === 'ok',
  });
  if (!ok) lost.add(1);
}