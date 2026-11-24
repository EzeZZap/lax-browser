# New tab focuses the URL bar

## What it does

Pressing **New tab** (sidebar button, browser menu, keyboard shortcut, or new private tab)
opens or selects a new-tab page and immediately focuses the sidebar URL/search field with the
text selected, so the user can type a URL or search without an extra tap.

The sidebar stays open (it is no longer closed on new tab from the sidebar).

## How it works

1. `openNewTabPage` selects an unused NTP or creates one.
2. The UI emits `UiEvent.FOCUS_ADDRESS`.
3. The browser screen opens the sidebar if needed and sets `focusUrlBar`.
4. The sidebar `UrlBar` requests focus and selects all text on focus.

## Related features

- **Clone tab** — duplicates an existing page; does not focus the URL bar.
- **Home toolbar action** — still opens the NTP without forcing address focus (different intent).
