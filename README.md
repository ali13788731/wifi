# DualNet — Wi-Fi + Mobile Data Download Manager (Android)

Downloads **one file through Wi-Fi and mobile data at the same time** and merges the pieces into one verified file. Also shows live speed in the notification and as a number in the status-bar icon.

## How dual-network works (real, no simulation)
1. `NetworkManager` asks Android for the Wi-Fi and the cellular `Network` (`requestNetwork`) and tracks *available / connected / internet-validated*.
2. `NetworkBindingManager` builds one OkHttp client per `Network`: `network.socketFactory` + `network.getAllByName` for DNS, its own connection pool, HTTP/1.1 only — so Wi-Fi sockets can never leak onto mobile and vice-versa.
3. `RangeProbe` never trusts `Accept-Ranges`: HEAD, then a real `Range: bytes=0-1023` request that must return **206**, a matching `Content-Range` and exactly the right byte count. Otherwise dual mode is disabled for that file and a normal single download is used.
4. The file is split into small segments kept in a shared queue. Every connection of every network pulls the next segment, so the faster network automatically takes more (throughput-proportional, dynamic). Failed segments go back to the queue and the other network finishes them. A lost network stops receiving segments.
5. Bytes are written with positional `FileChannel` writes to non-overlapping ranges of a pre-allocated temp file. Segments (start/end, bytes, network, times, retries, validated) are stored in Room so downloads **resume after app restart**.
6. Before a file is marked complete: all segments done+validated, contiguous, sizes add up, file length equals `Content-Length`. Only then it is moved to your SAF folder / app folder.

## Android limits (cannot be bypassed)
- An app **cannot turn mobile data on**. The user enables it; the app detects it and offers a shortcut to the system panel.
- Some phones keep the cellular radio idle while Wi-Fi is on; `requestNetwork` asks Android to bring it up, but the OEM decides. The Network screen shows what actually happened.
- Status-bar speed icon: some phones hide *silent* notification icons — enable "show silent notification icons" in system notification settings.
- Foreground service type is `dataSync` (Android 15 limits it to ~6 h per run; downloads restart from saved segments).

## Features
Queue, priorities, max concurrent downloads, connections per network, speed limiter, monthly mobile-data limit + warning, Wi-Fi-only / mobile-only / Wi-Fi-first / dual modes, scheduled start, automatic retry, resume, pause/cancel, custom User-Agent / Referer / Cookies / Basic auth, redirects, Content-Disposition names, smart categories, history, open/share/delete, SAF folder, duplicate handling, diagnostics screen, dark/light, Persian (RTL) and English.

## Build
```
gradle assembleDebug          # app/build/outputs/apk/debug
gradle testDebugUnitTest      # HTTP 200/206/401/416, redirects, headers, segment verification, limiter
```
Release: set `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` then `gradle assembleRelease` (R8 enabled).
CI (`.github/workflows/build-apk.yml`) runs the unit tests and uploads both APKs.

## Dual-network acceptance test (needs a real phone)
Wi-Fi connected + mobile data on, a Range-capable server with a ≥100 MB file:
```
gradle connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.url=https://host/100MB.bin
```
It asserts: both byte counters increase, both transfer simultaneously, final size matches, counters sum to the file size. It is skipped (not passed) if both networks are not available.

## Before publishing on a store
Change `applicationId` (currently `com.example.netspeed`), add your own keystore, privacy policy, and test on several vendors.

## v2.2 changes
- Splash shows once per fresh start (after the app was fully closed), never on minimise/restore (`launchMode=singleTask`, splash tied to a new activity instance).
- Clipboard detection also runs when the splash ends, and only opens the "new download" window for real file links (known file types / download URLs / silent server probe).
- Status-bar speed icon: Wi-Fi number over mobile number with a divider line, bigger digits and a unit letter (K / M / G / T).
- Settings is now a menu; every group is its own page.
- Downloads tab: filter chips, labelled Stop / Resume / Retry button on EVERY download, "Pause all" / "Resume all" only when relevant.
- Usage and History filters open from one button (bottom sheet).
- New modern theme, animated home screen, gradient navigation pill.

## v2.3 changes
- Downloads: exact Wi-Fi / mobile bytes (and share) per download, live while running.
- History redesigned (grouped by day, per-file split bar); search, filter and "clear finished" appear only when there is history. Filter sheets open fully.
- Start / resume / retry / resume-all show a clear message naming the network that is off.
- Persian mode: all digits and units in Persian (کیلوبایت، مگابایت…), right-to-left notification, language follows live changes.
- Speed numbers no longer animate; home screen simplified; new "New download" sheet.

## v2.4 changes
- Downloads can use ONE network if you tick Settings > Downloads > "Allow downloading with only one network" (default: both required; the "network is off" dialog has a button that opens this setting).
- Duplicate links are refused ("this download is a duplicate").
- The "New download" sheet pastes a link you copied in the browser as soon as you come back; long-press menu (Cut/Copy/Paste/Select all) works in the link field.
- Status-bar speed icon is wide: Wi-Fi left, mobile right, bigger digits.
- Home: empty state removed; bottom bar no longer overlaps the last list row.
- Cafe Bazaar one-time purchase gate (release builds).

## Selling on Cafe Bazaar (300,000 toman, not shareable)
Plain "paid app" listings do not stop sharing: the APK works on any phone. This project uses a free listing + ONE non-consumable in-app product (Poolakey). Ownership is checked against the Bazaar account signed in on the phone, so a copy sent to a friend asks them to buy it.
1. Bazaar developer panel: publish the app, create an in-app product (non-consumable/permanent), e.g. id `full_version`, price 300,000 toman. Copy the app's RSA public key from the panel.
2. Build the release with your values:
   `gradle assembleRelease -PbazaarRsaKey="<RSA_KEY>" -PbazaarSku=full_version` (or env `BAZAAR_RSA_KEY`), signed with your own keystore.
3. Release builds require the purchase; debug builds skip it. A purchase is cached for 7 days for offline use and refreshed whenever Bazaar is reachable (a refund removes access).
Limit: no client-side check is crack-proof; R8 is on, but a determined cracker can patch the APK.

## v2.5 changes
- **Purchase reminder notification**: if Cafe Bazaar confirms the account has NOT bought the full version, a notification lists what the full version gives (once a day, at most 5 times; stops the moment it is bought). Android 13+: the notification permission is asked on the purchase screen.
- **New-version notification with release notes**: the app (also in the background, every 12 h, and when opened) reads `update.json` from this repo (`UPDATE_URL`, override with `-PupdateUrl=...`, empty = off). When `versionCode` there is higher than the installed one, one notification per version shows the notes (fa/en) and opens the `url` (your Bazaar page).
  To announce a release: bump `versionCode`/`versionName` in `app/build.gradle.kts`, then edit `update.json` (same numbers + notes) and push to `main` AFTER the new version is live on Bazaar.

## Signing the release APK on GitHub
Repo > Settings > Secrets and variables > Actions > New repository secret:
`KEYSTORE_BASE64` (base64 of the .jks), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `BAZAAR_RSA_KEY`.
The workflow decodes the keystore on the runner and signs `app-release.apk`; the log prints its SHA-256 fingerprint. Keep a backup of the .jks: the same key is needed for every future update.

## v2.6 — Free + Pro
The app is **free** and never shows a purchase wall. **Pro** is one non-consumable Cafe Bazaar product (`BAZAAR_SKU`, default `full_version`).

| | Free | Pro |
|---|---|---|
| Download with Wi-Fi **and** mobile data at the same time | — (one network at a time: Wi-Fi first, mobile data when Wi-Fi is off/lost, automatic switch) | ✅ |
| Queue, resume, history, notifications, status-bar speed, themes, Persian/English | ✅ | ✅ |
| Parallel downloads / connections per network | up to 2 / 4 | up to 5 / 8 |
| Speed limiter | — | ✅ |
| Monthly mobile-data cap + warning | — | ✅ |
| Scheduled start | — | ✅ |
| Custom User-Agent / Referer / cookies / login per link | — | ✅ |
| Usage statistics per network | — | ✅ |
| Download history (search, filter) | — | ✅ |

How it is enforced: `ProState` (singleton) holds the entitlement, `LicenseManager` asks Bazaar and grants/revokes it, `Entitlements` clamps the settings and request options **inside the download engine** (`DownloadManager`, `DownloadJob`), not only in the UI. Pro stays active offline for 7 days after the last successful Bazaar check. Debug builds are always Pro. To move a feature between tiers edit `core/license/Entitlements.kt` (and the lock in the matching screen).

Size: unused navigation libraries removed, only `en`/`fa` library resources kept, extra R8 options (`-repackageclasses`, `-allowaccessmodification`) and fewer packaged metadata files.
