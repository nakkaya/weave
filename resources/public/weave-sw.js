// Weave Service Worker

self.addEventListener('push', function(event) {
    let data = {
        title: 'Notification',
        body: '',
        icon: '/icon-192.png',
        badge: '/icon-192.png',
        url: '/'
    };

    if (event.data) {
        try {
            const payload = event.data.json();
            data = {
                title: payload.title || data.title,
                body: payload.body || data.body,
                icon: payload.icon || data.icon,
                badge: payload.badge || data.badge,
                url: payload.url || data.url,
                tag: payload.tag,
                data: payload.data
            };
        } catch (e) {
            data.body = event.data.text();
        }
    }

    const options = {
        body: data.body,
        icon: data.icon,
        badge: data.badge,
        tag: data.tag,
        data: {
            url: data.url,
            ...data.data
        },
        requireInteraction: false,
        silent: false
    };

    event.waitUntil(
        self.registration.showNotification(data.title, options)
    );
});

self.addEventListener('notificationclick', function(event) {
    event.notification.close();

    const urlToOpen = event.notification.data?.url || '/';
    const targetUrl = new URL(urlToOpen, self.location.origin).href;

    event.waitUntil(
        self.clients.matchAll({ type: 'window', includeUncontrolled: true })
            .then(clientList => {
                for (const client of clientList) {
                    if (client.url === targetUrl && 'focus' in client) {
                        return client.focus();
                    }
                }

                if (self.clients.openWindow) {
                    return self.clients.openWindow(targetUrl);
                }
            })
    );
});

self.addEventListener('notificationclose', function(event) {});

self.addEventListener('install', function(event) {
    self.skipWaiting();
});

self.addEventListener('activate', function(event) {
    event.waitUntil(self.clients.claim());
});
