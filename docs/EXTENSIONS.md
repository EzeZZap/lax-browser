# Firefox add-ons

GeckoView 158 ships the same WebExtensions engine Firefox for Android uses, so Lax runs
add-ons from addons.mozilla.org (AMO). GeckoView does **not** provide the UI around them, so the app
implements install prompts, the add-on manager, toolbar buttons, popups, and the mapping between
extension "tabs" and app tabs. All APIs below were checked against the GeckoView 158 sources.

Code: `engine/gecko/ExtensionsManager.kt` (engine side, behind the `Host` interface implemented by
`GeckoEngine`), `ui/extensions/ExtensionsScreen.kt` (manager screen, permission dialog, popup
sheet), and the "Extensions" section of the browser menu.

## Installing

- The Extensions screen (Settings → Extensions, or the browser menu) has a "Browse extensions"
  button that opens `https://addons.mozilla.org/android/` in a normal tab.
- `GeckoEngine.onLoadRequest` intercepts top-level navigations to `https://….xpi` (AMO's
  "Add to Firefox" button links to the signed XPI). Only navigations with a user gesture or direct
  navigations start an install; the navigation itself is denied.
- On addons.mozilla.org, Lax's built-in page helper (`assets/extensions/icons/amo.js`) renames
  "Add to Firefox" to "Add to Lax" (also the Spanish "Añadir/Agregar a Firefox") in links and
  buttons, including ones AMO adds later. It changes text only, never links or behavior. Firefox
  keeps extensions off AMO, so the helper requests `mozillaAddons`, which only built-in
  (privileged) extensions can use.
- Install uses `webExtensionController.install(url, INSTALLATION_METHOD_MANAGER)`. Gecko downloads
  the XPI, verifies Mozilla's signature and the blocklist, and keeps the add-on in the Gecko
  profile. Add-ons are not stored in Room; `controller.list()` restores them on startup.
- `WebExtensionController.PromptDelegate`:
  - `onInstallPromptRequest`: dialog with name, icon, Firefox-style permission warnings, declared
    data collection, and an "Allow in private browsing" checkbox (off by default). Resolves with
    `PermissionPromptResponse(granted, granted && private, false)`; technical-data collection is
    never opted in.
  - `onUpdatePrompt` and `onOptionalPrompt` (`permissions.request()`): the same dialog with
    Allow/Deny.
  - Requests wait in a queue and are shown one at a time, in order. The dialog only closes from
    its buttons or Back, not from a tap outside it. Every install reports when it ends, also when
    the prompt is cancelled, so a recommended add-on stops showing as installing.
- Errors: user cancellation is silent; blocklisted, incompatible, unsigned, and other failures
  show a snackbar.

## Add-on manager

Backed by `controller.list()` and `AddonManagerDelegate` (`onInstalled`, `onReady`, `onEnabled`,
`onDisabled`, `onUninstalled`). Built-in extensions (`isBuiltIn`, like the favicon helper) are
never listed. Per add-on:

- Enable/disable switch (`enable`/`disable` with `EnableSource.USER`). Add-ons disabled by Gecko
  (blocklist, signature, app version) show that state and cannot be switched on.
- "Extension settings" opens `metaData.optionsPageUrl` in a normal tab of the active Space.
  `TabDelegate.onOpenOptionsPage` does the same.
- "Allow in private browsing" (`setAllowedInPrivateBrowsing`). Rows show when an add-on runs in
  private browsing.
- "Permissions" lists the required permissions and host origins.
- "View on addons.mozilla.org" when `metaData.amoListingUrl` is set.
- "Remove" with confirmation (`uninstall`).

## Pinning

Extensions shown in the browser menu are "pinned". The install prompt has a "Show in menu" switch
(on by default) next to "Allow in private browsing"; the manager's menu changes it later. Pins are
stored in Room (`extension_prefs`, schema 5) because Gecko does not store app preferences for
add-ons, and are removed when an add-on is uninstalled. The manager lists enabled and disabled
add-ons in separate sections.

## Toolbar buttons and popups

- Each add-on gets `setActionDelegate`. The default browser action (title, icon, badge, enabled
  state) of each pinned add-on is shown in the browser menu's extensions section; a tap calls
  `action.click()`. Pinned add-ons without a toolbar button open their settings page instead.
  Per-tab overrides (`onBrowserAction` with a session) are ignored for now because the menu has no
  per-tab state. Page actions are not shown.
- `onTogglePopup`/`onOpenPopup` return an open session from
  `GeckoSessionFactory.createExtensionPopup()`, shown in a bottom sheet hosting a `GeckoView`.
  The popup has no container: it only shows the add-on's own page, which must share storage with
  the rest of the add-on. `window.close()` (`onCloseRequest`) closes the sheet; links that open new
  windows go to a normal tab.

## Tabs API mapping

- **`TabDelegate.onNewTab`** (`tabs.create`): `GeckoEngine.createExtensionTab` creates the session
  through `GeckoSessionFactory` with the active Space's container and registers the tab with
  `BrowserOperations.onExtensionOpenedTab`, then returns the unopened session (Gecko opens it and
  loads the URL). `cookieStoreId` is ignored, so an add-on can never pick another container. Add-ons
  never open private tabs; while private browsing is on, the tab opens in the background of the
  active Space. If the Space's container is missing, the request is refused.
- **`SessionTabDelegate`** is registered on every tab session (`extensions.attach` in
  `attachDelegates`, and for existing sessions when an add-on is registered).
  - `onUpdateTab` with `active: true` selects the tab (only within the current normal/private side).
  - `onCloseTab` closes ordinary tabs and denies pinned tabs and Essentials, which need the UI's
    own confirmation or unload behavior.
- **Active tab:** `webExtensionController.setTabActive(session, active)` is called whenever the
  engine changes a session's active state, so `tabs.query({ active: true })` is correct.
- **Unloaded tabs** have no Gecko session, so add-ons cannot see them through `tabs.query`.
- **Containers:** Firefox for Android has no `contextualIdentities` API. Session context IDs still
  isolate site storage for content scripts. Add-on storage (`storage.local`) is shared across
  containers, which is expected.

## Security and policy

- Only signed add-ons install (Gecko enforces signatures; `aboutConfigEnabled(false)` keeps the
  preferences locked). Only `https` XPI URLs are accepted.
- Permissions are shown before install and never auto-granted. Private browsing access is opt-in.
- Third-party add-ons cannot use `nativeMessaging` with the app: only the built-in favicon helper
  has a message delegate.

## Not implemented yet

- Background update checks (`controller.update`, for example a daily WorkManager job) and a manual
  "check for updates" action.
- Installing from a local file (`INSTALLATION_METHOD_FROM_FILE`).
- Per-tab browser action overrides, page actions, and pinning action buttons to the toolbar.
- Embedding options pages in the manager when `openOptionsPageInTab` is false (they always open in
  a tab).
