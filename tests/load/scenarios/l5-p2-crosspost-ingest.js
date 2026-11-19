import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { apiBaseUrl } from '../lib/config.js';

/**
 * QA-14 harness: crosspost source posts (VK wall_post_new) with fan-out on 3 targets in app.
 * PROFILE=full → 1000 posts/min (~16.7/s); smoke → 5/s.
 */
const duplicates = new Counter('crosspost_duplicate_posts');

export const options = {
  scenarios: {
    crosspost: {
      executor: 'constant-arrival-rate',
      rate: Number(
        __ENV.CROSSPOST_RATE ||
          (__ENV.PROFILE === 'full' ? Math.ceil(1000 / 60) : 5)
      ),
      timeUnit: '1s',
      duration: __ENV.CROSSPOST_DURATION || '30s',
      preAllocatedVUs: Number(__ENV.PROFILE === 'full' ? 80 : 5),
      maxVUs: Number(__ENV.PROFILE === 'full' ? 500 : 20),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
  },
};

const channelId = __ENV.CHANNEL_ID;
const secret = __ENV.VK_SECRET || 'load-secret';

export default function () {
  if (!channelId) {
    duplicates.add(1);
    return;
  }
  const postId = 100000 + __ITER;
  const payload = JSON.stringify({
    type: 'wall_post_new',
    event_id: `xp-${postId}-${Date.now()}`,
    group_id: __ENV.GROUP_ID || '1',
    secret,
    object: {
      id: postId,
      from_id: 1,
      owner_id: -(__ENV.GROUP_ID || 1),
      text: `crosspost load ${postId}`,
      post_type: 'post',
    },
  });
  const res = http.post(`${apiBaseUrl()}/hooks/vk/${channelId}`, payload, {
    headers: { 'Content-Type': 'application/json' },
    timeout: '2s',
  });
  check(res, { 'hook accepted': (r) => r.status === 200 });
  sleep(0.01);
}