import { getVapidPublicKey, subscribeWebPush, unsubscribeWebPush } from '../api/notifications-api';

const SW_PATH = '/sw-web-push.js';

function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = window.atob(base64);
  const output = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i += 1) {
    output[i] = raw.charCodeAt(i);
  }
  return output;
}

export function isWebPushSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    'Notification' in window
  );
}

export async function ensureWebPushServiceWorker(): Promise<ServiceWorkerRegistration | null> {
  if (!isWebPushSupported()) return null;
  return navigator.serviceWorker.register(SW_PATH);
}

export async function enableWebPush(workspaceId: string): Promise<'granted' | 'denied' | 'unsupported'> {
  if (!isWebPushSupported()) return 'unsupported';

  const permission = await Notification.requestPermission();
  if (permission !== 'granted') {
    return permission === 'denied' ? 'denied' : 'denied';
  }

  const registration = await ensureWebPushServiceWorker();
  if (!registration) return 'unsupported';

  const vapid = await getVapidPublicKey();
  const key = vapid.vapidPublicKey;
  if (!key) {
    throw new Error('VAPID public key is missing');
  }

  const existing = await registration.pushManager.getSubscription();
  const subscription =
    existing ??
    (await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(key) as BufferSource,
    }));

  const json = subscription.toJSON();
  const endpoint = json.endpoint;
  const p256dh = json.keys?.p256dh;
  const auth = json.keys?.auth;
  if (!endpoint || !p256dh || !auth) {
    throw new Error('Incomplete push subscription');
  }

  await subscribeWebPush(workspaceId, {
    endpoint,
    p256dh,
    auth,
    userAgent: navigator.userAgent,
  });

  return 'granted';
}

export async function disableWebPush(workspaceId: string): Promise<void> {
  if (!isWebPushSupported()) return;
  const registration = await navigator.serviceWorker.getRegistration(SW_PATH);
  const subscription = await registration?.pushManager.getSubscription();
  if (subscription) {
    await unsubscribeWebPush(workspaceId, subscription.endpoint);
    await subscription.unsubscribe();
  }
}
