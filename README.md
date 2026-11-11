# Batterly

Live charging watts and amps, battery drain history and a charge alarm.

**[Website](https://batterly.itsash.in)** · **[Download APK](https://github.com/its-ash/batterly/releases/latest/download/batterly.apk)** · Get it in **[Ash Store](https://store.itsash.in)**

## Features

- **Live charging data**, updated every second: current in mA (+ charging, - discharging), voltage,
  power in watts, level, temperature, charger type, health, status, cycle count (Android 14+) and the
  time to full or to empty.
- **Live chart** of watts or mA over the last two minutes.
- **Background monitor** (optional): an ongoing notification such as `+12.4 W · 2,450 mA · 63%`, and a
  sample logged every minute for 7 days. Starts again after a reboot.
- **Charge alarm**: a notification (and optional vibration) when the battery reaches your limit
  (default 80%), plus an optional low-battery reminder.
- **History**: level over 24 hours or 7 days, drain per hour with the worst hours highlighted, screen-on
  vs screen-off drain, and every charging session (start and end level, duration, average and peak watts).
- **Private**: no account, no analytics. The only network call is the update check in Settings.

## Install

- **Ash Store**: install [Ash Store](https://store.itsash.in) once, then install and update Batterly from it.
- **APK**: download the [latest APK](https://github.com/its-ash/batterly/releases/latest/download/batterly.apk) on your phone and open it. Allow your browser to install unknown apps if Android asks.

Requires Android 8.0 (API 26) or later. Allow notifications (Android 13+) and, for the background
monitor, set Batterly to "Unrestricted" or "Don't optimize" in battery settings so Android keeps it running.

Some phones do not report the charging current, or report it in different units or with the opposite
sign. Batterly corrects the unit and sign (see `CLAUDE.md`); without a current sensor it shows level,
voltage and temperature only.

## Develop

Needs Flutter ≥ 3.44 and the shared theme package at `../theme`.

```bash
make run           # run on a connected device
make test          # flutter analyze + flutter test
make test-native   # JVM tests for the Kotlin current normalizer and alarm logic
make build         # release APK
make deploy        # bump version, build, tag, push, GitHub release (see store.mk)
```

## Release

`make deploy` follows the Ash Store kit (`store.mk`, `store.json`): it bumps the
version in `pubspec.yaml`, builds, commits, tags `vX.Y.Z`, pushes, and publishes
a GitHub release with `batterly.apk`. Ash Store and the website pick up the new
version from that release.

## Project layout

```
lib/
├── main.dart
├── battery/
│   ├── battery_snapshot.dart     # live reading model (mirrors Snapshot.kt)
│   ├── battery_math.dart         # time to full / empty, formatting
│   ├── history_analysis.dart     # sessions, drain per hour, screen on/off (pure)
│   ├── battery_platform.dart     # MethodChannel + EventChannel to MainActivity.kt
│   ├── live_store.dart           # live stream + two-minute window
│   └── settings_store.dart       # settings and permissions
└── screens/
    ├── home_screen.dart          # Live / History tabs
    ├── live_screen.dart
    ├── history_screen.dart
    └── settings_screen.dart
android/app/src/main/kotlin/com/itsash/batterly/
├── CurrentMath.kt                # unit + sign normalization (pure)
├── AlarmLogic.kt                 # charge limit / low battery alerts (pure)
├── Snapshot.kt                   # BatterySnapshot + notification text
├── BatteryReader.kt              # BatteryManager + sticky battery intent
├── HistoryDb.kt                  # SQLite, 7 days
├── BatteryPrefs.kt               # shared settings
├── MonitorService.kt             # foreground service + BootReceiver
└── MainActivity.kt               # channels
android/app/src/test/…            # CurrentMathTest, AlarmTrackerTest, NotificationTextTest
```

See `CLAUDE.md` for details.

## License

No license file is included yet.

Built by [itsash.in](https://itsash.in).
