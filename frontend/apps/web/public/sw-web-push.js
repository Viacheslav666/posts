/* Posts FE-9 Web Push service worker (minimal). */
self.addEventListener('push', (event) => {
  let title = 'Posts';
  let body = 'Новое уведомление';
  let data = {};
  try {
    if (event.data) {
      const payload = event.data.json();
      title = payload.title || title;
      body = payload.body || payload.eventType || body;
      data = payload;
    }
  } catch {
    try {
      body = event.data ? event.data.text() : body;
    } catch {
      // ignore
    }
  }
  event.waitUntil(
    self.registration.showNotification(title, {
      body,
      data,
      icon: '/icon-192.png',
      badge: '/icon-192.png',
    }),
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.url) || '/app';
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windowClients) => {
      for (const client of windowClients) {
        if ('focus' in client) {
          client.navigate(url);
          return client.focus();
        }
      }
      if (clients.openWindow) {
        return clients.openWindow(url);
      }
      return undefined;
    }),
  );
});
