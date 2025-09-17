/* --- Basic PWA service worker --- */
const VERSION = 'v1.0.0';
const PRECACHE = `precache-${VERSION}`;
const RUNTIME  = `runtime-${VERSION}`;

// Daftar file shell yang ingin dipre-cache.
// Sesuaikan path (bisa diisi via Thymeleaf kalau perlu).
const PRECACHE_URLS = [
  '/',                // start_url
  '/offline.html',    // halaman offline sederhana
  '/promis.png',      // ikon kecil
  '/login.png'        // logo di halaman login
];

self.addEventListener('install', (event) => {
  // Precache & aktifkan SW secepatnya (opsional skipWaiting)
  event.waitUntil(
    caches.open(PRECACHE)
      .then((cache) => cache.addAll(PRECACHE_URLS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    // Enable navigation preload (jika tersedia)
    if (self.registration.navigationPreload) {
      try { await self.registration.navigationPreload.enable(); } catch {}
    }
    // Hapus cache versi lama
    const keys = await caches.keys();
    await Promise.all(keys
      .filter((k) => k !== PRECACHE && k !== RUNTIME)
      .map((k) => caches.delete(k)));
    // Klaim kontrol
    await self.clients.claim();
  })());
});

// Strategi fetch:
// - Navigasi (HTML): network-first -> fallback offline
// - Asset statis (GET same-origin): cache-first
self.addEventListener('fetch', (event) => {
  const { request } = event;

  // Hanya tangani GET
  if (request.method !== 'GET') return;

  // 1) Navigasi dokumen
  const isNavigation =
    request.mode === 'navigate' ||
    (request.headers.get('accept') || '').includes('text/html');

  if (isNavigation) {
    event.respondWith((async () => {
      try {
        // Coba preload (jika ada), else fetch jaringan
        const preload = await event.preloadResponse;
        return preload || await fetch(request);
      } catch {
        // Fallback ke cache shell atau offline.html
        const cache = await caches.open(PRECACHE);
        return (await cache.match('/offline.html')) ||
               new Response('Offline', { status: 503, headers: { 'Content-Type': 'text/plain' }});
      }
    })());
    return;
  }

  // 2) Asset statis same-origin: cache-first
  const url = new URL(request.url);
  const sameOrigin = url.origin === self.location.origin;

  if (sameOrigin) {
    event.respondWith((async () => {
      const cache = await caches.open(RUNTIME);
      const cached = await cache.match(request);
      if (cached) return cached;

      try {
        const resp = await fetch(request);
        // Cache hanya response OK & tipe basic/opaque aman
        if (resp && (resp.status === 200 || resp.type === 'opaqueredirect' || resp.type === 'basic')) {
          cache.put(request, resp.clone());
        }
        return resp;
      } catch {
        // Jika gagal & ada versi precache, pakai itu
        const fallback = await caches.match(request);
        if (fallback) return fallback;
        throw new Error('Fetch failed');
      }
    })());
  }
});

// (Opsional) terima pesan untuk update cepat dari halaman
self.addEventListener('message', (event) => {
  if (event.data === 'SKIP_WAITING') self.skipWaiting();
});


