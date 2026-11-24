# Downloads: Space attribution

## What it does

Each non-private download remembers the Space it started from (the tab’s Space). The Downloads
list shows that Space next to the host, and can be filtered by Space or “No Space”
(Essentials / unknown / deleted Space).

## How it works

- `Download.spaceId` / `DownloadEntity.space_id` (migration 14→15).
- `DownloadRequest.spaceId` is set from the originating tab in `GeckoEngine` (external
  responses, Glance, save link) and from `savePage`.
- Private downloads stay unlistable and do not store a Space.
- Filtering is UI-only; attribution is not changed after the download starts.

## Related features

- **Spaces & containers** — identity for attribution.
- **Save page** — ZIP downloads also carry the active tab’s Space.
