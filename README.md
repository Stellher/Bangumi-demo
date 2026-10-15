# Bangumi Glass · Android Compose Client

Independent Android project for [Bangumi](https://bgm.tv/), using Jetpack Compose, OAuth persistence/refresh, discovery, weekday calendar, anime-only seasonal browsing, search, subject/person details, collection editing, and a developer-facing catalog of 56 documented API operations.

## UX and upstream attribution

This snapshot integrates the **actual original** [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) **v1.0.0 Catalog source components** (LiquidBottomTabs, LiquidBottomTab, LiquidButton, LiquidSlider, LiquidToggle and gesture utilities) under `app/src/main/java/com/kyant/backdrop/catalog/`. The base Maven dependency `io.github.kyant0:backdrop:1.0.0` supplies the rendering engine, not those high-level controls. The sheet adapts the official Catalog DialogContent example. The applicable Apache-2.0 license and attribution are in `third_party/`.

This is an **independent code snapshot**, initialized in this repository rather than importing Git commits from another repository.

## Build locally

- Java 17, Android SDK Platform 36, Android Gradle Plugin 8.9.2 and **Gradle 8.11.1**
- Open the **repository root** in Android Studio, sync Gradle and run the `app` module.
- To build from a terminal with Gradle installed: `gradle :app:assembleDebug`
- A Gradle wrapper can be generated with `gradle wrapper --gradle-version 8.11.1`.
- CI generates `app/src/main/assets/bangumi_api_catalog.json` from the two checked-in specifications via `python scripts/generate_catalog.py`; if building locally from the repository checkout, run `python -m pip install PyYAML` and `python scripts/generate_catalog.py` first.
- The GitHub Actions workflow tests and builds an R8-optimized Release APK signed with a **temporary debug test certificate** (not production signing).

## OAuth setup

**WebView-free login:** A native Compose button opens Bangumi's official authorization page with AndroidX Browser Custom Tabs (system browser), not an embedded WebView. The Bangumi code callback returns via `bgmoauthdemo://callback`. Token exchange, token status, refresh, and authorized API calls use OkHttp. The user never enters a Bangumi password into this app.

Bangumi's published OAuth endpoints only support the interactive **Authorization Code** flow; a pure native username/password + OkHttp login is **not an official supported grant**. See [the detailed OAuth flow inspection](docs/OAUTH_NATIVE_FLOW.md) for the request sequence, optional safe HTTP entry probe, security limitations and rationale.

Configure the registered redirect URI `bgmoauthdemo://callback` in the Bangumi developer console. The public client ID is part of the build; enter the Bangumi OAuth client secret **on the device**, never in this public repository. OAuth tokens are stored using Android Keystore encryption and refreshed when supported by the provider. An OAuth refresh token is not necessarily permanent and access may ultimately need reauthorization.

## App structure

- `app/src/main/java/com/example/bangumioauthdemo/`: Kotlin Compose app, MVVM viewmodels and Bangumi data/network layer.
- `app/src/main/java/com/kyant/backdrop/catalog/`: unmodified Kyant v1.0.0 Catalog components and utilities.
- `app/src/test/`: seasonal-type/date and HTTP Content-Type regression tests.
- `specs/` and `scripts/generate_catalog.py`: documented Bangumi API catalog generation.
- `third_party/`: Apache 2.0 license and Catalog attribution.

## Data notes

The seasonal screen defaults to TV animation and additionally checks `type=2`, premiere date and platform. This is a catalog by release quarter, **not** an independent authoritative broadcast schedule. Private collections and account changes require appropriate Bangumi OAuth scope. Not all 56 API methods have dedicated high-level UI screens. The app has not been exhaustively tested against all authenticated API routes.

## Project history

This repository starts with a fresh Git history. The original development repository's commit chain was **not** merged, pushed or imported.
