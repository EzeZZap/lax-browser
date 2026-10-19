# Releasing Lax

## Signing key

Release builds are signed with `lax-release.jks` (alias `lax`) using the passwords in
`keystore.properties`, both in the project root and both ignored by git.

**Back both files up somewhere safe (a password manager or encrypted drive).** Android only
installs an update over an existing copy when it is signed with the same key; if the key is lost,
every user has to uninstall Lax (losing their data) to get a new version.

`keystore.properties`:

```properties
storeFile=lax-release.jks
storePassword=...
keyAlias=lax
keyPassword=...
```

For CI, the same values can come from the environment variables `LAX_KEYSTORE`,
`LAX_KEYSTORE_PASSWORD`, `LAX_KEY_ALIAS`, and `LAX_KEY_PASSWORD` (path relative to the project
root). Without a key file the release APKs are built unsigned.

## Versioning

- Bump `versionName` (semantic versioning, e.g. `0.2.0`) and `versionCode` (+1) in
  `app/build.gradle.kts` for every release.
- Each ABI's APK gets `versionCode * 10 + digit`: armeabi-v7a `1`, arm64-v8a `2`, x86_64 `3`
  (version 1 gives 11, 12, 13).
- Tag GitHub releases as `v<versionName>` (e.g. `v0.1.0`). The update checker compares the tag
  with the installed `versionName`.

## Building

There are two distribution flavors, each with a debug and a release build:

| Preset (Android Studio run menu) | Gradle task | Update checks |
| --- | --- | --- |
| Build GitHub debug | `assembleGithubDebug` | GitHub releases of `laxUpdateRepo` |
| Build GitHub release | `assembleGithubRelease` | GitHub releases of `laxUpdateRepo` |
| Build F-Droid debug | `assembleFdroidDebug` | none |
| Build F-Droid release | `assembleFdroidRelease` | none |

The presets live in `.run/`; Android Studio's Build Variants panel switches between the same four
variants (`githubDebug` is the default). Both flavors are the same app (same package name).

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testGithubDebugUnitTest lintGithubDebug assembleGithubRelease
```

The APKs are in `app/build/outputs/apk/<flavor>/<buildType>/`, for example
`app/build/outputs/apk/github/release/`:

- `app-github-arm64-v8a-release.apk`: almost all current phones and tablets.
- `app-github-armeabi-v7a-release.apk`: older 32-bit phones.
- `app-github-x86_64-release.apk`: emulators and Chromebooks.

Check the signature with:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\github\release\app-github-arm64-v8a-release.apk
```

Release builds run R8 over the whole app with the rules in `app/src/main/keepRules/rules.keep`:
Lax's own classes and GeckoView are kept whole and only the libraries are shrunk (about 11 MB less
per APK). Limiting R8 to some packages (`packageScope`) breaks startup, and a missing keep rule
only shows up at runtime, so CI starts every release APK on an emulator
(`.github/scripts/smoke-test.sh`) before it can be published. If a release build crashes on startup, the crash log and
`app/build/outputs/mapping/githubRelease/mapping.txt` show which class needs a keep rule.

To check a release build on your own device without the release key, build it with the debug key:
`.\gradlew.bat assembleGithubRelease -PlaxDebugSignRelease=true`.

## GitHub releases and update checks

The `github` builds check the repository set in `gradle.properties` (or `-PlaxUpdateRepo=`):

```properties
laxUpdateRepo=EzeZZap/lax-browser
```

Then create a release tagged `v<versionName>` and attach the three `github` release APKs. Each asset name must
contain its ABI (the default names do), so Lax can offer the right file. Release notes come from
the release body. Lax checks at most once a day and only when "Check for updates" is on.

"Install" in the update dialog downloads the APK into the app's cache and checks that it is Lax,
has a higher version code, and is signed with the same certificate as the installed app. Then it
hands it to the system package installer, which asks the user to confirm. The first time, Android
also asks the user to allow installs from Lax (`REQUEST_INSTALL_PACKAGES`). A release signed with a
different key is refused, so keep using `lax-release.jks`.

### Automated with GitHub Actions

- `.github/workflows/ci.yml` runs on every push to `main` and on pull requests:
  - `build`: unit tests, lint, debug builds, and an R8 release build signed with a throwaway
    debug key.
  - `device`: on an Android 14 emulator, starts that release APK and opens a link in it (the
    smoke test), then runs the instrumented tests with `connectedGithubDebugAndroidTest`: Gecko
    container isolation, database migrations, encrypted logins, the sidebar, activity recreation,
    and the search widget. Reports and the R8 mapping are uploaded as artifacts.
- `.github/workflows/release.yml` runs when you push a tag like `v0.1.0`: it checks the tag
  matches `versionName`, runs the unit tests (including one that requires a changelog section for
  the version), builds signed APKs with `laxUpdateRepo` set to the GitHub repository, starts the
  x86_64 APK on an emulator, names them `lax-v0.1.0-arm64-v8a.apk` (and so on), and publishes a
  GitHub release with them. The R8 mapping is kept as a workflow artifact for 90 days.
  Add these repository secrets first (Settings → Secrets and variables → Actions):
  - `LAX_KEYSTORE_BASE64`: in PowerShell,
    `[Convert]::ToBase64String([IO.File]::ReadAllBytes("lax-release.jks"))`
  - `LAX_KEYSTORE_PASSWORD`, `LAX_KEY_ALIAS` (`lax`), `LAX_KEY_PASSWORD`: from `keystore.properties`.

The app shows `CHANGELOG.md` (and `CHANGELOG.es.md` in Spanish) under Settings > About > What's
new, and the installed version's section once after an update. Rename the "Unreleased" heading to
the new `versionName` when releasing, and keep both files listing the same versions (a unit test
checks this).

To release: update `versionCode`, `versionName`, `CHANGELOG.md`, `CHANGELOG.es.md`, and the
`fastlane/metadata/android/*/changelogs/<versionCode * 10 + 1..3>.txt` files, commit, then
`git tag v0.2.0 && git push origin v0.2.0`.

## F-Droid

- F-Droid builds from source and signs with its own key, so users cannot switch between the
  F-Droid and GitHub builds without reinstalling (unless reproducible builds are set up).
- F-Droid builds the `fdroid` flavor (in its build metadata: `gradle: [fdroid]`), which has no
  update checker, so the option is not shown in the app.
- The APKs contain no Google dependency metadata block (`dependenciesInfo` is disabled) and no
  proprietary libraries. GeckoView comes from Mozilla's Maven repository, which F-Droid allows.
- The per-ABI version codes follow F-Droid's convention, so its metadata can use
  `VercodeOperation` with `10 * %c + 1/2/3`.
- The store listing lives in `fastlane/metadata/android/` (English and Spanish), which F-Droid
  reads from the repository. Add screenshots to `images/phoneScreenshots/` (and optionally
  `images/icon.png`, 512 × 512, and `images/featureGraphic.png`, 1024 × 500).