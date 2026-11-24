# Bang shortcuts

## What it does

In the address bar, `!<bang> <query>` searches a specific built-in engine instead of the
user’s default. Examples: `!g cats`, `!w Einstein`, `!ddg privacy`. Settings → Search
lists the supported bangs.

## How it works

- `BangShortcuts.resolve` maps bang tokens to `SearchEngines.builtIn` templates.
- `UrlInput.normalize` runs bang resolution before host/search handling, so navigation and
  submit both honor bangs.
- Address-bar suggestions: a resolved bang appears as an **Address** suggestion; the default
  “search with your engine” row is omitted for that query so it isn’t misleading.
- Unknown bangs or a bang with no query fall through to normal search/URL rules.
- Bangs always use Lax’s built-in engines (not a custom template).

Supported tokens include `g`/`google`, `b`/`bing`, `w`/`wiki`/`wikipedia`, `d`/`ddg`/`duckduckgo`,
`br`/`brave`, `sp`/`startpage`, `eco`/`ecosia`, `q`/`qwant`.

## Related features

- **Search engines** — default and custom templates in Settings.
- **Search suggestions** — remote suggestions still use the default engine for non-bang input.
