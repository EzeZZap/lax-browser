# Privacy

Lax does not have accounts, ads, analytics or telemetry. Browsing data is stored on the device.

## Data stored on the device

- Spaces, tabs, bookmarks, history, new tab page shortcuts, site settings and app settings
- Website data (cookies, storage, cache), kept separately for each container by GeckoView
- Saved logins, encrypted with a key held in the Android Keystore
- Recently closed tabs, the download list and crash logs

Android's backup service only includes the app database and the app language. Saved logins,
website data and crash logs are not backed up. Backups made from Settings > Data contain Spaces,
tabs, bookmarks, shortcuts, site settings and app settings, but no website data. They include
saved logins only if you choose to, encrypted with a password you set (AES-256-GCM with a key
derived by PBKDF2); only the number of logins is readable without it.

Exporting saved logins from Settings > Privacy > Saved logins writes an unencrypted CSV file, so
Lax asks for the screen lock first. After importing a password file, Lax offers to delete it.

## Network connections

Apart from the websites you visit, Lax connects to:

- **Your search engine**, for search suggestions while typing in the address bar. This is off in
  private tabs and can be turned off in Settings > Search.
- **api.github.com**, once a day, to check for a new release. Only in builds from GitHub; can be
  turned off in Settings > About. When you choose to update, the APK is downloaded from
  github.com. F-Droid builds do not check for updates.
- **Your Nextcloud server**, only if you connect one in Settings > Data: to log in (your
  Nextcloud's login page opens in a tab and gives Lax its own app password), and to upload or
  download the backup file `Lax-backup.json`. The app password is stored on the device encrypted
  with a key in the Android Keystore, and only sent to that server over HTTPS.
- **Websites' icon files**, to show favicons. These requests are sent without cookies.
- **addons.mozilla.org**, when you install an add-on. GeckoView and installed add-ons may also
  contact Mozilla services such as the add-on blocklist and Safe Browsing.

## Crash logs

If Lax crashes, it saves a log on the device with the stack trace, app and Android version, and
device model. Logs are only shared if you choose to share them.

## Permissions

Camera, microphone, location and notification permissions are requested only when a website or a
feature like QR code scanning or voice search needs them.
