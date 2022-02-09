/// <reference lib="webworker" />

importScripts('sw-runtime-resources-precache.js');
import { clientsClaim, cacheNames, WorkboxPlugin } from 'workbox-core';
import { matchPrecache, precacheAndRoute, getCacheKeyForURL } from 'workbox-precaching';
import { NavigationRoute, registerRoute } from 'workbox-routing';
import { PrecacheEntry } from 'workbox-precaching/_types';
import { NetworkOnly, NetworkFirst } from 'workbox-strategies';

declare var self: ServiceWorkerGlobalScope & {
  __WB_MANIFEST: Array<PrecacheEntry>;
  additionalManifestEntries?: Array<PrecacheEntry>;
};

self.skipWaiting();
clientsClaim();

declare var OFFLINE_PATH: string; // defined by Webpack/Vite

// Combine manifest entries injected at compile-time by Webpack/Vite
// with ones that Flow injects at runtime through `sw-runtime-resources-precache.js`.
let manifestEntries: PrecacheEntry[] = self.__WB_MANIFEST || [];
if (self.additionalManifestEntries?.length) {
  manifestEntries.push(...self.additionalManifestEntries);
}

const offlinePath = OFFLINE_PATH;

// Compute the registration scope path.
const scopePath = new URL(self.registration.scope).pathname;

/**
 * Replaces <base href> in pre-cached response HTML with the service worker’s
 * scope URL.
 *
 * @param response HTML response to modify
 * @returns modified response
 */
async function rewriteBaseHref(response: Response) {
  const html = await response.text();
  return new Response(html.replace(/<base\s+href=[^>]*>/, `<base href="${self.registration.scope}">`), response);
};

/**
 * Returns true if the given URL is included in the manifest, otherwise false.
 */
function isManifestEntryURL(url: URL) {
  return manifestEntries.some((entry) => getCacheKeyForURL(entry.url) === getCacheKeyForURL(`${url}`));
}

/**
 * A workbox plugin that checks and updates the network connection status
 * on every fetch request.
 */
let connectionLost = false;
function checkConnectionPlugin(): WorkboxPlugin {
  return {
    async fetchDidFail() {
      connectionLost = true;
    },
    async fetchDidSucceed({ response }) {
      connectionLost = false;
      return response
    }
  }
}

const networkOnly = new NetworkOnly({
  plugins: [checkConnectionPlugin()]
});
const networkFirst = new NetworkFirst({
  plugins: [checkConnectionPlugin()]
});

if (process.env.NODE_ENV === 'development') {
  self.addEventListener('activate', (event) => {
    event.waitUntil(caches.delete(cacheNames.runtime));
  });

  registerRoute(
    ({ url }) => url.pathname.startsWith(`${scopePath}VAADIN/__vite_ping`),
    networkOnly
  );

  registerRoute(
    ({ url }) => url.pathname.startsWith(`${scopePath}VAADIN/`),
    networkFirst
  );

  if (offlinePath === '.') {
    registerRoute(
      ({ request, url }) => request.mode === 'navigate' && !isManifestEntryURL(url),
      async ({ event }) => {
        return networkFirst
          .handle({ request: new Request(offlinePath), event })
          .then(rewriteBaseHref);
      }
    )
  }
}

registerRoute(
  new NavigationRoute(async (context) => {
    const serveResourceFromCache = async () => {
      // Serve any file in the manifest directly from cache
      if (isManifestEntryURL(context.url)) {
        return await matchPrecache(context.request);
      }

      const offlinePathPrecachedResponse = await matchPrecache(offlinePath);
      if (offlinePathPrecachedResponse) {
        return await rewriteBaseHref(offlinePathPrecachedResponse);
      }
      return undefined;
    };

    // Use offlinePath fallback if offline was detected
    if (!self.navigator.onLine) {
      const precachedResponse = await serveResourceFromCache();
      if (precachedResponse) {
        return precachedResponse;
      }
    }

    // Sometimes navigator.onLine is not reliable, use fallback to offlinePath
    // also in case of network failure
    try {
      return await networkOnly.handle(context);
    } catch (error) {
      const precachedResponse = await serveResourceFromCache();
      if (precachedResponse) {
        return precachedResponse;
      }
      throw error;
    }
  })
);

precacheAndRoute(manifestEntries);

self.addEventListener('message', (event) => {
  if (typeof event.data !== 'object' || !('method' in event.data)) {
    return;
  }

  // JSON-RPC request handler for ConnectionStateStore
  if (event.data.method === 'Vaadin.ServiceWorker.isConnectionLost' && 'id' in event.data) {
    event.source?.postMessage({ id: event.data.id, result: connectionLost }, []);
  }
});
