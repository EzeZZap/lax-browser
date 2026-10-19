# Changelog

## 0.3.3

- Update checks are now opt-in: the last page of the first-run setup has a switch for them, and
  they stay off unless you turn them on. Existing installs keep their current setting

## 0.3.2

- Reorganized author names in source code. The donate button now opens ko-fi.com/ezezzap

## 0.3.1

- Sites without HTTPS open again: an address typed without "https://" falls back to "http://"
  when the secure connection fails, unless HTTPS-only mode is on

## 0.3.0

- Back up to your own Nextcloud, by hand or automatically once a day, and restore from it.
  Saved logins can be included, encrypted with a password you choose
- Backups made from Settings now say when they are saved or restored

## 0.2.0

- Swipe on the toolbar's address to switch tabs (left and right) or Spaces (up and down). Each
  direction can instead open the sidebar or the menu, turn split view on or off, open or close a
  tab, reload, or edit the address, in Settings > Toolbar & gestures
- The search widget can be set to a Space, so its tabs open in that Space's container. Without
  one, it uses the Space that is open
- "Check for updates now" says when Lax is already up to date

## 0.1.0

Initial release.

### Tabs and Spaces

- Vertical tab sidebar with Essentials, pinned tabs, folders and regular tabs
- Sidebar can be placed on the left or right, and is docked on wide screens
- Spaces with separate containers, emoji or color icons and per-Space theme color
- Setting for which Space links from other apps open in, and an "Open in Lax…" share target
- Pinned tabs and Essentials keep their starting page
- Automatic tab unloading after a configurable time
- Split view and link previews
- Tab search across Spaces
- Recently closed tabs are kept across restarts

### Privacy and security

- Private tabs, with an optional lock behind the screen lock
- Enhanced Tracking Protection, HTTPS-only mode and Global Privacy Control
- Per-site permissions and site data clearing
- Saved logins stored encrypted on the device, with a screen lock prompt to view passwords
- Import and export of saved logins as CSV (Firefox, Chrome, Edge, Brave, Bitwarden)
- Password generator
- Support for Android autofill services

### Other

- Firefox add-ons from addons.mozilla.org
- Bookmarks with folders and HTML import/export
- History, new tab page shortcuts and local backups, optionally with saved logins encrypted by a password
- Downloads continue in the background, with progress in a notification
- Error pages for offline, unreachable and insecure sites
- Reader view, translation, find in page, per-site zoom, desktop mode and print to PDF
- Installable web apps and Custom Tabs support
- Option to open links in installed apps
- Search bar widget for the home screen
- First-run setup for layout, Spaces, Essentials, search and add-ons
- Update checks and installs for builds from GitHub
- English and Spanish translations
