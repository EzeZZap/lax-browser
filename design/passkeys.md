# Passkeys

## What it does

Websites can create and use passkeys while browsing in Lax. Passkeys are **not** stored in
Lax. They go through Android’s Credential Manager into whatever password manager the user has
enabled as a system credential provider (for example 1Password, Bitwarden, or Google Password
Manager).

## How it works

1. GeckoView handles WebAuthn in the page.
2. On Android 14+, GeckoView calls Credential Manager with the website origin set. The host app
   must declare:
   - `android.permission.CREDENTIAL_MANAGER_SET_ORIGIN`
   - `android.permission.CREDENTIAL_MANAGER_QUERY_CANDIDATE_CREDENTIALS`
3. The system shows the user’s enabled credential providers. Creating or using a passkey happens
   in that provider’s UI.
4. If Credential Manager is unavailable or declines, GeckoView falls back to GMS FIDO2. That path
   needs a `GeckoRuntime.ActivityDelegate`, wired by `WebAuthnSupport` on every Activity that
   shows web content (`MainActivity`, `CustomTabActivity`, `WebAppActivity`).

## Related features

- **Saved logins** — Lax’s own encrypted password store. Separate from passkeys. The Saved logins
  screen explains passkeys and links to system autofill settings so the user can enable their
  external manager.
- **Android autofill** — password fill from system services; passkeys use Credential Manager.

## Limitations

- Requires Android 14+ for the Credential Manager path (external providers). Older devices use the
  FIDO2 fallback when available.
- Some providers only trust browsers on a privileged-caller allowlist (package name + signing
  certificate). Debug or unsigned builds may not appear in every manager until the release
  signing key is approved by that provider.
- Lax cannot list, edit, or delete passkeys; that stays in the password manager / system UI.
