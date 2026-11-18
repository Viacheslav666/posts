import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { apiBaseUrl } from '../lib/config.js';

/**
 * BE-32 load: ~2000 webhook events/s against one api instance.
 * Requires a pre-seeded channel + subscription secret via env:
 *   CHANNEL_ID, VK_SECRET, API_BASE_URL
 *
 * Target: p99 < 200ms, zero loss (all 200 OK).
 */
const lost = new Counter('social_inbound_hook_lost');

export const options = {
  scenarios: {
    inbound: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.INBOUND_RATE || 2000),
      timeUnit: '1s',
      duration: __ENV.INBOUND_DURATION || '30s',
      preAllocatedVUs: 200,
      maxVUs: 1000,
    },
  },
  thresholds: {
    http_req_duration: ['p(99)<200'],
    http_req_failed: ['rate<0.01'],
    social_inbound_hook_lost: ['count==0'],
  },
};

const channelId = __ENV.CHANNEL_ID;
const secret = __ENV.VK_SECRET;

export default function () {
  if (!channelId || !secret) {
    lost.add(1);
    return;
  }
  const eventId = `load-${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
    type: 'wall_post_new',
    event_id: eventId,
    group_id: __ENV.GROUP_ID || '1',
    secret,
    object: {
      id: __ITER,
      from_id: 1,
      owner_id: -1,
      date: Math.floor(Date.now() / 1000),
      post_type: 'post',
      text: 'load',
    },
  });
  const res = http.post(`${apiBaseUrl()}/hooks/vk/${channelId}`, payload, {
    headers: { 'Content-Type': 'application/json' },
    timeout: '1s',
  });
  const ok = check(res, {
    'status 200': (r) => r.status === 200,
    'body ok': (r) => String(r.body).trim() === 'ok',
  });
  if (!ok) {
    lost.add(1);
  }
}
