# NTP shortcut folders

## What it does

New-tab page shortcuts can be grouped into folders. Folders are browser-wide (not
Space-scoped). Users can create, rename, reorder, collapse, and delete folders; delete
returns shortcuts to the NTP root. Shortcuts can move into or out of a folder from their
long-press menu.

## How it works

- Models: `ShortcutFolder` and `Shortcut.folderId` (null = root).
- Persistence: `shortcut_folders` table + `shortcuts.folder_id` (migration 14→15).
- Domain ops mirror tab folders: create / rename / collapse / move / delete /
  `moveShortcutToFolder`. Reordering a shortcut only moves within its group.
- Backup includes `shortcutFolders` and optional `folderId` on shortcuts.
- UI: folder sections on the NTP plus a create-folder control next to “Shortcuts”.

## Related features

- **NTP shortcuts** — flat tiles at the root and inside folders.
- **Tab folders** — Space-scoped pinned-tab folders (separate feature).
