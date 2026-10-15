# Bangumi OAuth: native Android + OkHttp, with no WebView

Official reference: https://github.com/bangumi/api/blob/master/docs-raw/How-to-Auth.md

## Research result (2026-10-15)

Bangumi's public OAuth documentation explicitly supports **Authorization Code** only.
`response_type=code` is the only public authorization response type. An officially
documented Resource Owner Password Credentials (`grant_type=password`) login
endpoint or device-code login endpoint is **not available**.

Therefore a third-party application cannot obtain arbitrary users' OAuth consent
entirely via native email/password fields plus OkHttp against a supported API.
Direct POSTs to Bangumi's website login form would emulate private site state
and need handling for cookies, anti-CSRF and CAPTCHA; this is fragile and
not equivalent to the documented OAuth flow. The app must not request or
transmit user passwords. We do not bypass CAPTCHA or intercept session cookies.

## Actual flow implemented

| Step | UI/network owner | Details |
|---|---|---|
| 1. User clicks log in | Native Compose button | Generate cryptographically random `state` |
| 2. Open official authorization URL | AndroidX Browser Custom Tabs | `GET https://bgm.tv/oauth/authorize?response_type=code&client_id=...&redirect_uri=...&state=...` |
| 3. Login / consent | Trusted external browser | Browser retains cookies, username, password, CAPTCHA and consent state |
| 4. Return callback | Android deep link `bgmoauthdemo://callback` | Validate URI and constant-time compare `state`; reject duplicate values |
| 5. Exchange code (valid 60 seconds) | OkHttp `FormBody` | `POST https://bgm.tv/oauth/access_token`, `grant_type=authorization_code` |
| 6. Authenticated API calls | OkHttp | `Authorization: Bearer ...` to `https://api.bgm.tv/v0/...` |
| 7. Expiry / rotation | OkHttp + WorkManager | Same token endpoint with `grant_type=refresh_token`, persist newest pair with Android Keystore |

Custom Tabs displays the real Bangumi HTTPS origin inside a system-managed
browser surface. **This is not WebView.** An installed browser which lacks
Custom Tabs can open via `ACTION_VIEW` as fallback. For a device without
a browser, the app shows an actionable error.

A client secret entered into a mobile app remains extractable by someone
who controls that device; Keystore encryption prevents ordinary storage
disclosure but does not make a client secret truly confidential. For a
public production mobile app, use a small trusted token-exchange backend,
and add PKCE if the identity provider supports it. Bangumi does not
document PKCE here; do not silently pretend it is supported.

## Safe, opt-in HTTP probe

In OAuth Settings, **“用 OkHttp 检查授权入口 HTTP 跳转”** sends exactly one
unauthenticated GET request using an independent OkHttp client:

- `followRedirects(false)`, `CookieJar.NO_COOKIES`
- The UI displays only response HTTP code, media type, redirect host and path
- Response body, `Set-Cookie`, redirect query, login password, CAPTCHA,
  client secret, authorization code and access/refresh tokens are not captured

This is a limited *unauthenticated HTTP flow inspection*, **not a packet
capture of the user's system browser**. Browser sessions are isolated from
third-party apps. A redirect might differ by device/network and cannot
prove the complete user-authenticated flow. Actual end-to-end testing
requires the user to approve OAuth on their own device/account.

## Security notes

- Allow only `https://bgm.tv/oauth/authorize` with exact registered client
  ID, redirect URI, `response_type=code` and nonempty `state`.
- State checking precedes OAuth error or code handling.
- Do not print authorization codes or raw tokens to diagnostic UI.
- The app's token/code exchange and refresh use OkHttp exclusively.
- Authorization can be revoked and refresh tokens may expire; don't
  promise eternal sessions beyond the provider's guarantees.

## Relevant code

- `OAuthBrowserLauncher.kt`: Custom Tabs with origin allow-list/fallback
- `OAuthRepository.kt`: OkHttp exchange, refresh, status, public HTTP probe
- `OAuthViewModel.kt`: anti-CSRF state and safe callback
- `MainActivity.kt`: native Compose authorization entry
- `OAuthSessionManager.kt` / `SecureSessionStore.kt`: encrypted session
- `OAuthBrowserLauncherTest.kt`: tests for malicious URL rejection
