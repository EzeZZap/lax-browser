# Per-Space cookie and site-data wipe

## What it does

From **Spaces & containers**, each Space’s menu offers **Clear cookies and site data**. That
deletes Gecko storage for the Space’s container (cookies, local storage, and related site data)
and reloads any open tabs that use that container.

## How it works

- Domain: `BrowserOperations.clearSpaceSiteData` emits `EngineCommand.ClearContainerData` with the
  Space’s `containerId`, plus `Reload` for loaded tabs in that container.
- Engine: `storageController.clearDataForSessionContext(containerId)` (same path as deleting an
  unused container).
- If other Spaces share the container, the confirmation lists them — clearing is container-scoped,
  not Space-row-scoped.

## Related features

- **Containers** — storage partition identity; wiping a Space wipes its container.
- **Tracking protection Space override** — privacy controls on the same Spaces screen.
- **Clear browsing data** (Settings) — global clear across every container.
