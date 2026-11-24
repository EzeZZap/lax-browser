# Image context menu

## What it does

Long-pressing an image on a page offers:

- **Open image in new tab**
- **Save image** (download, anonymous fetch — no cookies)
- **Share image** (fetches into a temporary cache file and opens the system share sheet)
- **Copy image address** (the image URL)

Link long-presses keep the existing link actions (open, Glance, copy/share link, and so on).

## How it works

Gecko reports the context element to `GeckoEngine`, which builds a `ContextMenuTarget`. The
browser UI shows `ContextMenuDialog`.

Share uses `GeckoEngine.fetchToCacheFile` (anonymous / private-aware fetch), writes under
`cacheDir/share/`, and shares through the app `FileProvider` (`cache-path` name `share`).

## Related features

- **Downloads** — Save image goes through the normal download pipeline.
- **Copy / share link** — parallel actions for link targets on the same menu.
