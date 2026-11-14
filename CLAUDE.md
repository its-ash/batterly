# Batterly (package: batterly / com.itsash.batterly)

Battery monitor: live charging current and watts, drain history, charge alarm, battery health
(estimated capacity, cycles), Bluetooth device batteries. Native Kotlin reads the battery and runs the
background service; Flutter is the UI (Live, History, Health, Devices tabs + Settings).

## Code layout

- `android/app/src/main/kotlin/com/itsash/batterly/`
  - `CurrentMath.kt`: pure normalization (no Android types), tests in `CurrentMathTest.kt`.
  - `AlarmLogic.kt`: `AlarmTracker`, fires each alert once per crossing, re-arms on unplug / drop.
    `DeviceAlarmTracker`: Bluetooth low-battery alert per device address, re-arms at threshold + 5.
  - `HealthLogic.kt` (pure, `HealthLogicTest`): `CapacityTracker` estimates real capacity as
    Δ`CHARGE_COUNTER` / Δlevel × 100 between the first and last level change of an uninterrupted charge
    or discharge (≥ 20%, 1000–25000 mAh, level changes seen within 3 min only). Counter < 30 000 is read
    as mAh. `CycleLogic`: percent charged (only rises) for the cycle estimate.
  - `DeviceBattery.kt`: design capacity from hidden `PowerProfile` (reflection); Android 14+ state of
    health / manufacturing date / first-use date / charging policy (`BatteryManager` property ids 10/7/8/9,
    @SystemApi) which need `BATTERY_STATS`, grantable only with
    `adb shell pm grant com.itsash.batterly android.permission.BATTERY_STATS`.
  - `BluetoothBatteries.kt`: paired devices with hidden `getBatteryLevel()` / `isConnected()` (reflection)
    and the `BATTERY_LEVEL_CHANGED` broadcast. Needs `BLUETOOTH_CONNECT` (Android 12+).
  - `Snapshot.kt`: `BatterySnapshot` (wire map) and `NotificationText` (`+12.4 W · 2,450 mA · 63%`;
    `iconText` for the status-bar number). `StatusIcon.kt` draws it as a bitmap small icon.
  - `BatteryReader.kt`: sticky `ACTION_BATTERY_CHANGED` + `BatteryManager` properties
    (CURRENT_NOW, falling back to CURRENT_AVERAGE, CHARGE_COUNTER).
  - `HistoryDb.kt`: SQLite `history.db` (v2), table `samples` pruned to 7 days, table `capacity`
    (estimates, newest 500 kept; `clearHistory` leaves it). Written by the service, read by the app over
    the channel (Dart never opens the DB).
  - `BatteryPrefs.kt`: SharedPreferences `batterly_prefs`, shared by app and service. The service also
    keeps `charged_percent`, `cycle_since_ms`, `last_level` (cycle estimate) there.
  - `MonitorService.kt`: `specialUse` foreground service + `BootReceiver`. Handler tick every 5 s,
    also reacts to battery, screen and Bluetooth-battery broadcasts, logs a sample about every 60 s and on
    screen on/off, feeds `CapacityTracker` and the cycle count. Holds no wake lock (battery app), so deep
    sleep can leave gaps; analysis ignores gaps over 10 min. `ACTION_REFRESH` redraws the notification.
  - `BatterlyWidget.kt` + `WidgetLogic.kt`: 2x2 resizable home-screen widget (`res/layout/widget_batterly.xml`,
    `res/xml/batterly_widget_info.xml`, `res/values*/colors.xml`). Reads via `BatteryReader`. `MonitorService`
    pushes updates (about every 3 s while charging, otherwise on state change or every 5 min); the system also
    refreshes every 30 min. The service stores the last charge's peak watts in `BatteryPrefs`. `WidgetLogic`
    (pure, tested) has the throttle, peak tracking, graph geometry and a Kotlin copy of the Dart
    time-to-full formula. `MoreWidgets.kt`: `BatterlySmallWidget` (1x1, `widget_small.xml`) and
    `BatterlyGraphWidget` (4x2, `widget_graph.xml`, 24 h level graph drawn as a bitmap from `HistoryDb`,
    redrawn at most once a minute). `BatterlyWidget.updateAll` updates all three.
  - `MainActivity.kt`: channels (below), permission screens.
- `lib/battery/`: models and logic (`battery_snapshot`, `battery_math`, `history_analysis`, `health`
  are pure and tested), `battery_platform` (channels, `BatteryPlatform` interface for fakes), `live_store`,
  `settings_store`. `health.dart`: capacity = median of the newest 5 estimates, health % vs design.
- `lib/screens/`: home (tabs; live stream runs only while the Live tab is visible and the app is in
  the foreground), live, history, health, devices (Bluetooth list, polled every 10 s), settings (ends with
  `UiUpdateSection(storeId: 'batterly')` then `UiDeveloperSection()`).
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
- `getConfig()` -> `{monitor, chargeAlarm, chargeLimit, lowReminder, lowLevel, vibrate, tempUnit ("c"|"f"),
  statusIcon ("icon"|"level"|"watts"), btAlert, btLowLevel, running}`
- `setConfig({key, value})`: keys as above except `running`; `monitor` starts/stops the service,
  `statusIcon` redraws its notification.
- `status()` -> `{notifications, batteryOptimizationsIgnored, hasCurrentSensor, bluetooth, sdk}`
- `openPermission({name})`: `notifications` (system dialog once, then app notification settings) | `battery`
  | `bluetooth` (dialog, then app settings).
- `samples({sinceMs})` -> `[{ts, level, currentMa?, voltageMv, tempC, plugged, screenOn}]`, oldest first.
- `clearHistory()`: samples only.
- `health()` -> `{designMah?, estimates: [{ts, mah, fromLevel, toLevel, charging}], chargedPercent, cycleSinceMs,
  cycleCount?, statsGranted, stateOfHealth?, manufactureDateMs?, firstUseDateMs?, chargingPolicy?}`
- `bluetoothDevices()` -> `[{address, name, kind, connected, level?}]`, connected first.

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
- Dart is formatted at 120 columns: `dart format -l 120 <files>`. Plain `dart format` uses 80 and
  reflows the whole codebase.
- Only network use: `UiUpdateSection` in Settings. No analytics.

## Release (Ash Store)

- `store.json` (id `batterly`, repo its-ash/batterly), `store.mk`, `index.html` + `CNAME`
  (`batterly.itsash.in`). See `../ash-store/store-kit/STORE.md`.
- `make deploy` = bump version → build → commit, tag `vX.Y.Z`, push → GitHub release with
  `batterly.apk` → topics, homepage, Pages. Never create releases, tags or asset names by hand.
- Do not edit `store.mk` here. Edit `../ash-store/store-kit/store.mk`, then run `make store-sync`
  at the workspace root.
