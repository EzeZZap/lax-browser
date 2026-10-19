"use strict";

// Collects <link rel="icon"> style declarations of the top-level document and sends them to the
// app. Only URLs and metadata are sent; the app decides what to download.
(function () {
  const ICON_RELS = new Set(["icon", "shortcut", "apple-touch-icon", "apple-touch-icon-precomposed", "fluid-icon"]);
  const MAX_ICONS = 32;
  let lastPayload = "";

  function collect() {
    const icons = [];
    for (const link of document.querySelectorAll("link[rel][href]")) {
      const rels = (link.getAttribute("rel") || "").toLowerCase().split(/\s+/);
      if (!rels.some((rel) => ICON_RELS.has(rel))) {
        continue;
      }
      icons.push({
        href: link.href,
        sizes: link.getAttribute("sizes") || "",
        type: link.getAttribute("type") || "",
        rel: rels.join(" "),
      });
      if (icons.length >= MAX_ICONS) {
        break;
      }
    }
    const payload = JSON.stringify(icons);
    if (payload === lastPayload) {
      return;
    }
    lastPayload = payload;
    browser.runtime.sendNativeMessage("laxbrowser", { type: "icons", icons }).catch(() => {});
  }

  collect();

  // Single-page apps often swap their icon after load.
  let timer = 0;
  const observer = new MutationObserver(() => {
    clearTimeout(timer);
    timer = setTimeout(collect, 500);
  });
  if (document.head) {
    observer.observe(document.head, { childList: true, subtree: true, attributes: true, attributeFilter: ["href", "rel"] });
  }
})();
