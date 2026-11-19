import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { apiBaseUrl } from '../lib/config.js';

/**
 * QA-14 harness: crosspost source posts (VK wall_post_new).
 * PROFILE=full → 1000 posts/min (~17/s); smoke → 5/s.
 * Hook acceptance only. Duplicate/loop counts are asserted by Qa14CrosspostLoopsIT, not by this counter.
 */
const lost = new Counter('crosspost_hook_lost');

export const options = {
  scenarios: {
    crosspost: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.CROSSPOST_RATE || (__ENV.PROFILE === 'full' ? Math.ceil(1000 / 60) : 5)),
      timeUnit: '1s',
      duration: __ENV.CROSSPOST_DURATION || '30s',
      preAllocatedVUs: Number(__ENV.PROFILE === 'full' ? 80 : 5),
      maxVUs: Number(__ENV.PROFILE === 'full' ? 500 : 20),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    'http_req_duration{scenario:crosspost}': [
      `p(95)<${Number(__ENV.CROSSPOST_P95_MS || (__ENV.PROFILE === 'full' ? 60000 : 120000))}`,
      `p(99)<${Number(__ENV.CROSSPOST_P99_MS || (__ENV.PROFILE === 'full' ? 120000 : 180000))}`,
    ],
    crosspost_hook_lost: ['count==0'],
  },
};

const channelId = __ENV.CHANNEL_ID;
const secret = __ENV.VK_SECRET || '';

export default function () {
  if (!channelId || !secret) {
    lost.add(1);
    return;
  }
  const postId = `${__VU}-${__ITER}`;
  const payload = JSON.stringify({
    type: 'wall_post_new',
    event_id: `xp-${postId}`,
    group_id: __ENV.GROUP_ID || '1',
    secret,
    object: {
      id: Number(__ITER) + __VU * 1_000_000,
      from_id: 1,
      owner_id: -Number(__ENV.GROUP_ID || 1),
      text: `crosspost load ${postId}`,
      post_type: 'post',
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