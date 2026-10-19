# Architecture

Lax uses GeckoView directly, without Android Components. Android Components expects its own
`BrowserStore` to own the tab list, and Lax needs its own model for Spaces, containers, and
Essentials, so the engine sits behind an app-owned interface instead.

## Code layout

```
domain/     Models and pure operations on BrowserState (unit-tested, no Android code)
state/      BrowserController: runs operations, persists the result, and tells the engine
data/       Room database, migrations, login and recently-closed stores
engine/     BrowserEngine interface and the GeckoView implementation (engine/gecko)
ui/         Compose screens: browser, sidebar, new tab page, settings, onboarding, ...
pwa/        Web app manifests, eligibility, launcher shortcuts, WebAppActivity
customtabs/ Custom Tabs for links opened from other apps
downloads/  Downloader and its foreground service
platform/   Screen lock, update checks and installs, crash log, default browser
widget/     Home screen search widget
```

## Data flow

1. The UI calls `BrowserController` (through `BrowserViewModel`).
2. A pure operation in `BrowserOperations` returns a new `BrowserState`, or a rejection.
3. The controller publishes the state, diffs the persisted part (`DataDiff`), and writes the
   changes in one Room transaction on a background queue.
4. `GeckoEngine.reconcile` opens, activates, and closes Gecko sessions to match the state.

The UI never owns browser state, and Gecko session objects are never stored. A tab row keeps its
URL and title, and the session history is saved separately (`tab_session_state`), so a tab can be
unloaded and restored later.

## Spaces and containers

- Each Space has one container. A container is a GeckoView session context ID
  (`GeckoSessionSettings.Builder.contextId`), which gives it its own cookies and site storage.
- Every session is created by `GeckoSessionFactory`, which refuses to build one without a known
  container, so no tab can end up in the default storage.
- Moving a tab to a Space with a different container unloads it and reloads its address in the
  new container. Site data stays where it was.
- Deleting a Space keeps its container and data. A container can only be deleted when nothing uses
  it, and deleting it clears its Gecko storage.
- Private tabs use their own context and are only kept in memory.

## Tabs

- **Essentials** have no Space and appear in every Space. Closing one unloads it.
- **Pinned tabs** belong to one Space and keep their starting page. Unloading keeps the tab;
  closing asks first.
- Tabs are `ACTIVE`, `OPEN`, or `UNLOADED` at runtime. Inactive tabs are unloaded after the time
  set in Settings > Tabs, except the tab playing media.

## Storage

- Room database (`data/`), with a migration and a migration test for every schema change. Schemas
  are exported to `app/schemas`.
- Saved logins are a JSON document encrypted with AES-GCM using a key in the Android Keystore
  (`files/logins.enc`). Backups can include them, encrypted with a password the user picks
  (PBKDF2 + AES-GCM, see `LoginBackup`).
- Backups go to a local file or to the user's Nextcloud (`NextcloudClient`: Login Flow v2 for an
  app password, then a WebDAV `PUT`/`GET` of `Lax-backup.json` in their files). The account is
  kept in `files/nextcloud.enc`, encrypted like saved logins; automatic backups run at most once a
  day, when Lax starts.
- Restoring tolerates bad data: unreadable rows are skipped, tabs whose Space is missing are kept
  aside, and Spaces with a missing container are shown as blocked.

## Testing

- `app/src/test`: unit tests for the domain code and parsers (`./gradlew testDebugUnitTest`).
- `app/src/androidTest`: instrumented tests for container isolation with the real engine, database
  migrations, encrypted logins, and UI (`./gradlew connectedDebugAndroidTest`).
- CI runs both, and starts every release APK on an emulator (`.github/scripts/smoke-test.sh`)
  before it is published, since R8 problems only show up at runtime.

## Known limitations

- Favicons appear once a site has been visited; until then bookmarks and shortcuts show a letter.
  Icon requests go through Gecko without cookies, so the shared HTTP cache may serve an icon that
  was fetched in another container.
- Installed web apps are pinned launcher shortcuts that open a standalone activity, like Firefox
  for Android does. They are not WebAPKs and do not appear in the app drawer. Android does not let
  apps delete pinned shortcuts, so removing one disables it.
- Per-site zoom uses CSS `zoom`, which scales the layout rather than zooming like a pinch.
- A horizontal scroller that starts inside the 24 dp edge-swipe zone loses drags that begin there.
- GeckoView 158 has no completion callback for clearing a container's data.
- Not implemented: background web push, folder uploads, and the add-on features listed at the end
  of [EXTENSIONS.md](EXTENSIONS.md).
- Firefox Sync is not implemented. A third-party browser needs its own OAuth client ID from
  Mozilla's accounts team to sign in to Mozilla accounts, plus Mozilla's application-services
  libraries (Rust, prebuilt per CPU), whose sync engines expect Android Components' storage rather
  than Lax's Room database. Nextcloud backups are the open alternative in the meantime.
