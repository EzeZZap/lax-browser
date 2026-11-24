# Clone tab

## What it does

Duplicates a tab as a new ordinary tab with the same address (and title/favicon when known).

- Not pinned and not Essential, even if the source was.
- Same Space and container as the source (Essentials clone into the **active** Space).
- Private tabs stay private.
- Desktop-site mode is copied when set.
- When a saved Gecko session state exists for the source, it is copied so back/forward history
  can restore; otherwise the clone loads the stored URL.

Available from the sidebar tab menu (and Essentials long-press) and from the browser menu for the
current page.

## How it works

- Domain: `BrowserOperations.cloneTab`
- Controller: `BrowserController.cloneTab` also copies `SessionStateStore` data to the new tab ID
- Empty new-tab pages fall through to `openNewTabPage` instead of creating a useless duplicate

## Related features

- **Move to Space** — relocates a tab; clone keeps the source Space
- **Recently closed** — restore uses the same session-state store
