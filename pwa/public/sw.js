const CACHE = 'playboard-shell-v3';
const CORE = ['/', '/index.html', '/manifest.webmanifest', '/icons/icon-192.png', '/icons/icon-512.png'];

self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    const index = await fetch('/index.html', { cache: 'reload' });
    await cache.put('/index.html', index.clone());
    const html = await index.text();
    const builtAssets = [...html.matchAll(/(?:src|href)="([^"#]+)"/g)]
      .map(match => match[1])
      .filter(url => url.startsWith('/') && !url.startsWith('/api/'));
    await Promise.all([...new Set([...CORE, ...builtAssets])].map(url => cache.add(url).catch(() => undefined)));
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET' || new URL(event.request.url).origin !== self.location.origin) return;

  const request = event.request;
  if (new URL(request.url).pathname.startsWith('/api/')) return;
  event.respondWith(
    fetch(request)
      .then(response => {
        if (response.ok) {
          const copy = response.clone();
          caches.open(CACHE).then(cache => cache.put(request, copy));
        }
        return response;
      })
      .catch(() => caches.match(request).then(cached => cached || caches.match('/index.html'))),
  );
});
// Web push (FCM). Mirrors pushNotificationFrom() in src/push-core.ts — keep them in sync.
self.addEventListener('push', event => {
  let payload = {};
  try { payload = event.data ? event.data.json() : {}; } catch (e) { payload = {}; }
  const n = payload.notification || {};
  const data = payload.data || {};
  const title = n.title || data.title || 'Playboard';
  const body = n.body || data.body || '';
  const query = [
    data.groupId ? `group=${encodeURIComponent(data.groupId)}` : '',
    data.matchId ? `match=${encodeURIComponent(data.matchId)}` : '',
  ].filter(Boolean).join('&');
  const url = data.url || (data.matchId ? `/matches${query ? `?${query}` : ''}` : data.groupId ? `/board?${query}` : '/');
  event.waitUntil(self.registration.showNotification(title, {
    body,
    icon: '/icons/icon-192.png',
    badge: '/icons/icon-192.png',
    data: Object.assign({}, data, { url }),
  }));
});

self.addEventListener('notificationclick', event => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.url) || './';
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then(windows => {
      const existing = windows.find(w => 'focus' in w);
      if (existing) { existing.navigate(url); return existing.focus(); }
      return clients.openWindow(url);
    }),
  );
});
