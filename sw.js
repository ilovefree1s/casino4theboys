// Lets Chrome install the casino as an app. It keeps nothing: every request
// goes straight to the network, so a new build is always the one you get.
self.addEventListener("install", function () { self.skipWaiting(); });
self.addEventListener("activate", function (e) { e.waitUntil(self.clients.claim()); });
self.addEventListener("fetch", function (e) {
  if (e.request.method !== "GET") return;
  e.respondWith(fetch(e.request));
});
