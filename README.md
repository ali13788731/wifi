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
