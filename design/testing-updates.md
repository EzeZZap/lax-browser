# Testing updates (prerelease channel)

## What it does

Lax can check GitHub Releases for newer APKs and install them in-app. By default only
**stable** releases are considered (`/releases/latest`, which never returns a prerelease).

**Get testing versions** (Settings → About → Updates) is an opt-in toggle. When it is on,
update checks also look at GitHub **prereleases** and offer the newest build that is newer
than the installed `versionName`. The user still confirms download and the system installer
still asks before replacing the app.

Off by default. Turning it on does not send anything about the user; the app only reads the
public release list for `laxUpdateRepo`.

## How it works

- Setting: `BrowserSettings.preferTestingUpdates` (Room `prefer_testing_updates`, default `0`).
- Automatic daily checks and **Check for updates now** pass `includePrereleases` from that setting.
- With testing off: `GET …/releases/latest`.
- With testing on: `GET …/releases?per_page=30`, skip drafts, pick the newest by SemVer
  (including prerelease suffixes such as `0.3.4-testing.2`).
- Update dialog uses a different body when `UpdateInfo.isPrerelease` is true.
- Version comparison treats `1.0.0` as newer than `1.0.0-testing.1`, and
  `1.0.0-testing.2` as newer than `1.0.0-testing.1`.

## Publishing a testing build

1. Set `versionName` to something like `0.3.4-testing.1` and bump `versionCode` in
   `app/build.gradle.kts`.
2. Changelog: either add `## 0.3.4-testing.1` or reuse the core section `## 0.3.4`
   (the unit test accepts either for prerelease version names).
3. Commit, then tag and push: `git tag v0.3.4-testing.1 && git push origin v0.3.4-testing.1`.
4. `.github/workflows/release.yml` builds signed APKs and publishes a **prerelease** whenever
   the tag contains `-` (e.g. `v0.3.4-testing.1`). Stable tags like `v0.3.4` stay full releases.

Users with the toggle on (and automatic updates or **Check now**) can install that APK the
same way as a stable update. Obtainium and anyone without the toggle keep seeing only stables.

## Related

- In-app updates and signing: [docs/RELEASING.md](../docs/RELEASING.md)
- Settings / About UI: `SettingsScreen` updates section
- Engine: `UpdateChecker`, `GitHubReleases`, `Versions`, `UpdateInstaller`
