# Tracking protection: Space override

## What it does

Each Space can override the global Enhanced Tracking Protection level (Standard / Strict / Off),
or inherit the global setting. Configure it under **Spaces & containers → Space menu → Tracking
protection**.

Essentials and private tabs always use the global setting.

## How it works

- Persisted on `Space.trackingProtection` (`null` = inherit). Room migration 12→13.
- `BrowserState.effectiveTrackingProtection(tab)` resolves Space override vs global.
- On reconcile, the **runtime** ETP list level follows the **active** Space (or global). Session
  `useTrackingProtection` is set per open tab from that tab’s effective level (`false` when Off).
- GeckoView’s ETP *lists* (Standard vs Strict) are process-wide; switching Spaces updates the
  runtime level. Background tabs from another Space may briefly share the active Space’s list
  until that Space is active again — on/off remains per session.

## Related features

- **Settings → Tracking protection** — the global default.
- **Per-Space site-data wipe** — other Space privacy action on the same screen.
