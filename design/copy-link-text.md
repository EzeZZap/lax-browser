# Copy link text

## What it does

Long-pressing a link in a page shows **Copy link text** when GeckoView provides visible
anchor text. That copies the link’s label (not the URL). **Copy link address** still copies
the href.

## How it works

- GeckoView 158 `ContentDelegate.ContextElement.linkText` is passed through
  `ContextMenuTarget.linkText` (trimmed, capped at 500 characters).
- The context menu only offers the action when `linkText` is non-blank.
- Clipboard reuse: `BrowserViewModel.copyToClipboard` (same path as copying the address).

## Related features

- **Image context menu** — share / save / open image.
- **Copy link address** — existing href copy action.
