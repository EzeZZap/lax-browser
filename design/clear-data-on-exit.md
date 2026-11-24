# Clear data on exit

## What it does

An opt-in Privacy setting, **Clear data when I leave**, clears browsing history, cookies,
site storage, auth sessions, and caches whenever Lax leaves the foreground (for example when
the user switches apps). Open Gecko sessions are unloaded first so they cannot re-accumulate
data. Tab records, bookmarks, shortcuts, and saved logins stay.

## How it works

- Setting: `BrowserSettings.clearDataOnExit` (Room `clear_data_on_exit`, migration 13→14).
- On `MainActivity.onStop` (when not a configuration change), `BrowserViewModel.onAppBackground`
  calls `clearDataOnExit()` if the switch is on.
- That unloads every loaded tab (`BrowserOperations.unloadAllLoaded` → engine reconcile closes
  sessions), clears history for `HistoryRange.ALL`, then
  `StorageController.clearData` with cookies, DOM storage, auth sessions, and caches.
- No toast: the action runs every time the user leaves with the setting enabled.
- Android may kill the process without `onStop` in rare cases; clearing then does not run
  (same limitation as other shutdown work on Android).

## Related features

- **Clear browsing data** — one-shot clear from Settings with category choices.
- **Per-Space cookie wipe** — clears one container without leaving the app.
- **Private tabs lock** — also triggered from `onAppBackground`.
