"use strict";

// Applies the site's zoom level chosen in Lax. The app sends {type: "zoom", percent} on the port;
// GeckoView has no page zoom API, so the page is scaled with CSS zoom like desktop zoom.
(function () {
  let port;
  try {
    port = browser.runtime.connectNative("laxbrowser");
  } catch (e) {
    return;
  }
  port.onMessage.addListener((message) => {
    if (!message || message.type !== "zoom" || typeof message.percent !== "number") {
      return;
    }
    const percent = Math.min(300, Math.max(30, message.percent));
    const root = document.documentElement;
    if (!root) {
      return;
    }
    root.style.zoom = percent === 100 ? "" : String(percent / 100);
  });
})();
