# Save page as ZIP

## What it does

The browser menu offers **Save page…** on a loaded http(s) tab. Lax builds a ZIP containing
`index.html` plus a `resources/` folder of linked stylesheets, images, scripts, and media, and
saves it through the normal Downloads pipeline.

## How it works

- GeckoView has no MHTML / “save complete” API (only `saveAsPdf`). A built-in WebExtension
  (`assets/extensions/savepage`, id `savepage@laxbrowser.javzzap.com`) connects a native
  messaging port and, on `{type:"save"}`, collects the document and fetches resources with the
  page’s cookies (`credentials: "include"`).
- HTML attribute URLs that were downloaded are rewritten to relative `resources/…` paths.
- Caps: 80 resources, 4 MB each, ~32–40 MB total, 5 MB HTML. Oversized or failed fetches are
  skipped; CSS `url()` backgrounds and cross-origin blocked assets may be missing offline.
- Native: `SavePageExtension` → `PageArchive.zip` → `Downloader.start` as `application/zip`.
- Private tabs still save the file but are not listed in Downloads history (same as other
  private downloads).

## Related features

- **Print or save as PDF** — Gecko `saveAsPdf` via the system print dialog.
- **Downloads** — shared save path and completion toast.
