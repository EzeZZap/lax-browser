"use strict";

// On demand, collects the top-level document and linked resources (with the page's cookies)
// and streams them to the app as a ZIP-friendly archive. Caps sizes so a huge page cannot
// flood native messaging.
(function () {
  const MAX_RESOURCES = 80;
  const MAX_EACH = 4 * 1024 * 1024;
  const MAX_TOTAL = 32 * 1024 * 1024;
  const MAX_HTML = 5 * 1024 * 1024;

  let port;
  try {
    port = browser.runtime.connectNative("laxbrowser-save");
  } catch (e) {
    return;
  }

  function abs(url) {
    try {
      return new URL(url, document.baseURI).href;
    } catch (e) {
      return null;
    }
  }

  function pathFor(url, index) {
    let name = "file";
    try {
      const u = new URL(url);
      name = (u.pathname.split("/").pop() || "file").split("?")[0];
    } catch (e) {}
    name = String(name)
      .replace(/[^\w.\-]+/g, "_")
      .replace(/^\.+/, "")
      .slice(0, 64);
    if (!name) name = "file";
    if (name.indexOf(".") < 0) name += ".bin";
    return "resources/" + index + "-" + name;
  }

  function toBase64(buf) {
    const bytes = new Uint8Array(buf);
    let binary = "";
    const chunk = 0x8000;
    for (let i = 0; i < bytes.length; i += chunk) {
      binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
    }
    return btoa(binary);
  }

  async function fetchResource(url) {
    const res = await fetch(url, { credentials: "include", cache: "force-cache" });
    if (!res.ok) throw new Error("bad status");
    const buf = await res.arrayBuffer();
    if (buf.byteLength > MAX_EACH) throw new Error("too large");
    const mime = (res.headers.get("content-type") || "application/octet-stream").split(";")[0].trim();
    return { data: toBase64(buf), mime: mime || "application/octet-stream", size: buf.byteLength };
  }

  function collectUrls() {
    const urls = [];
    const seen = new Set();
    function add(raw) {
      if (raw == null || raw === "") return;
      const url = abs(raw);
      if (!url || !/^https?:/i.test(url) || seen.has(url) || urls.length >= MAX_RESOURCES) return;
      seen.add(url);
      urls.push(url);
    }
    document.querySelectorAll("img[src]").forEach(function (el) {
      add(el.getAttribute("src"));
    });
    document.querySelectorAll("link[rel][href]").forEach(function (el) {
      const rel = (el.getAttribute("rel") || "").toLowerCase();
      if (rel.indexOf("stylesheet") >= 0 || rel.indexOf("icon") >= 0) add(el.getAttribute("href"));
    });
    document.querySelectorAll("script[src]").forEach(function (el) {
      add(el.getAttribute("src"));
    });
    document.querySelectorAll("source[src], video[src], video[poster], audio[src]").forEach(function (el) {
      add(el.getAttribute("src"));
      add(el.getAttribute("poster"));
    });
    return urls;
  }

  function rewriteHtml(map) {
    const clone = document.documentElement.cloneNode(true);
    clone.querySelectorAll("[src], [href], [poster]").forEach(function (el) {
      ["src", "href", "poster"].forEach(function (attr) {
        if (!el.hasAttribute(attr)) return;
        const url = abs(el.getAttribute(attr));
        if (url && map[url]) el.setAttribute(attr, map[url]);
      });
    });
    return "<!DOCTYPE html>\n" + clone.outerHTML;
  }

  async function collect() {
    const urls = collectUrls();
    const map = Object.create(null);
    let total = 0;
    const resources = [];
    for (let i = 0; i < urls.length; i++) {
      const url = urls[i];
      try {
        const r = await fetchResource(url);
        total += r.size;
        if (total > MAX_TOTAL) break;
        const path = pathFor(url, i);
        map[url] = path;
        resources.push({ path: path, mime: r.mime, data: r.data });
      } catch (e) {}
    }
    const html = rewriteHtml(map);
    if (html.length > MAX_HTML) throw new Error("html too large");
    return { title: document.title || "", html: html, resources: resources };
  }

  port.onMessage.addListener(function (msg) {
    if (!msg || msg.type !== "save") return;
    collect()
      .then(function (page) {
        port.postMessage({ type: "meta", title: page.title, html: page.html });
        for (let i = 0; i < page.resources.length; i++) {
          const r = page.resources[i];
          port.postMessage({ type: "resource", path: r.path, mime: r.mime, data: r.data });
        }
        port.postMessage({ type: "done" });
      })
      .catch(function (e) {
        port.postMessage({ type: "error", message: String((e && e.message) || e) });
      });
  });
})();
