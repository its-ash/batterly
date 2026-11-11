# Batterly (package: batterly / com.itsash.batterly)

Battery monitor: live charging current and watts, drain history, charge alarm. Native Kotlin reads the
battery and runs the background service; Flutter is the UI (Live, History, Settings).

## Code layout

- `android/app/src/main/kotlin/com/itsash/batterly/`
  - `CurrentMath.kt`: pure normalization (no Android types), tests in `CurrentMathTest.kt`.
  - `AlarmLogic.kt`: `AlarmTracker`, fires each alert once per crossing, re-arms on unplug / drop.
  - `Snapshot.kt`: `BatterySnapshot` (wire map) and `NotificationText` (`+12.4 W · 2,450 mA · 63%`).
  - `BatteryReader.kt`: sticky `ACTION_BATTERY_CHANGED` + `BatteryManager` properties
    (CURRENT_NOW, falling back to CURRENT_AVERAGE, CHARGE_COUNTER).
  - `HistoryDb.kt`: SQLite `history.db`, table `samples`, pruned to 7 days. Written by the service,
    read by the app over the channel (Dart never opens the DB).
  - `BatteryPrefs.kt`: SharedPreferences `batterly_prefs`, shared by app and service.
  - `MonitorService.kt`: `specialUse` foreground service + `BootReceiver`. Handler tick every 5 s,
    also reacts to battery and screen broadcasts, logs a sample about every 60 s and on screen on/off.
    Holds no wake lock (battery app), so deep sleep can leave gaps; analysis ignores gaps over 10 min.
  - `MainActivity.kt`: channels (below), permission screens.
- `lib/battery/`: models and logic (`battery_snapshot`, `battery_math`, `history_analysis` are pure and
  tested), `battery_platform` (channels, `BatteryPlatform` interface for fakes), `live_store`,
  `settings_store`.
- `lib/screens/`: home (tabs; live stream runs only while the Live tab is visible and the app is in
  the foreground), live, history, settings (ends with `UiUpdateSection(storeId: 'batterly')` then
  `UiDeveloperSection()`).
- `android/app/src/main/res/drawable/ic_stat_battery.xml`: white notification icon.

## Current normalization (CurrentMath.kt)

`CURRENT_NOW` is documented as microamps with + while charging, but vendors differ.
1. Unit: |raw| > 20 000 is microamps (divide by 1000), otherwise milliamps. Known limit: a phone that
   reports microamps and idles below 20 mA is read as milliamps.
2. Sign: the charging status wins. CHARGING gives +|mA|, DISCHARGING gives -|mA|; FULL / NOT_CHARGING
   / UNKNOWN keep the raw sign. Not plugged in with status CHARGING is treated as discharging.
Power = mA × mV / 1e6 W, signed like the current.

## Channel contract

MethodChannel `com.itsash.batterly/control` (`lib/battery/battery_platform.dart`):
- `getConfig()` -> `{monitor, chargeAlarm, chargeLimit, lowReminder, lowLevel, vibrate, tempUnit ("c"|"f"), running}`
- `setConfig({key, value})`: keys as above except `running`; `monitor` starts/stops the service.
- `status()` -> `{notifications, batteryOptimizationsIgnored, hasCurrentSensor}`
- `openPermission({name})`: `notifications` (system dialog once, then app notification settings) | `battery`.
- `samples({sinceMs})` -> `[{ts, level, currentMa?, voltageMv, tempC, plugged, screenOn}]`, oldest first.
- `clearHistory()`

EventChannel `com.itsash.batterly/live`: every second while listened, a map
`{ts, level, currentMa?, voltageMv, tempC, plugged (none|ac|usb|wireless|dock), status (charging|discharging|not_charging|full|unknown), health, cycleCount?, chargeCounterUah?, screenOn}`.
Keep the Kotlin `Snapshot.kt` / `CurrentMath.kt` names and the Dart enums in sync.

## Commands

- `make run`, `make build`, `make test` (analyze + Dart tests), `make test-native` (Kotlin JVM tests,
  needs one `make build` first), `make clean`.
- Icon: `assets/icon/*.svg` → `rsvg-convert -w 1024 -h 1024` to `*-1024.png`, then
  `dart run flutter_launcher_icons`.

## Conventions

- minSdk 26. UI only from `../theme` (read `../theme/COMPONENTS.md`).
- Only network use: `UiUpdateSection` in Settings. No analytics.

## Release (Ash Store)

- `store.json` (id `batterly`, repo its-ash/batterly), `store.mk`, `index.html` + `CNAME`
  (`batterly.itsash.in`). See `../ash-store/store-kit/STORE.md`.
- `make deploy` = bump version → build → commit, tag `vX.Y.Z`, push → GitHub release with
  `batterly.apk` → topics, homepage, Pages. Never create releases, tags or asset names by hand.
- Do not edit `store.mk` here. Edit `../ash-store/store-kit/store.mk`, then run `make store-sync`
  at the workspace root.
