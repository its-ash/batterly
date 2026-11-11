/// Charger connected to the phone (`plugged` on the channel).
enum PlugType {
  none('None'),
  ac('AC charger'),
  usb('USB'),
  wireless('Wireless'),
  dock('Dock');

  const PlugType(this.label);
  final String label;

  static PlugType parse(Object? key) => values.firstWhere((p) => p.name == key, orElse: () => none);
}

/// Charging status (`status` on the channel; mirrors `ChargeState` in `CurrentMath.kt`).
enum ChargeStatus {
  charging('Charging', 'charging'),
  discharging('Discharging', 'discharging'),
  notCharging('Not charging', 'not_charging'),
  full('Full', 'full'),
  unknown('Unknown', 'unknown');

  const ChargeStatus(this.label, this.key);
  final String label;
  final String key;

  static ChargeStatus parse(Object? key) => values.firstWhere((s) => s.key == key, orElse: () => unknown);
}

/// Battery health (`health` on the channel).
enum BatteryHealth {
  good('Good'),
  overheat('Overheated'),
  dead('Dead'),
  overVoltage('Over voltage'),
  failure('Failure'),
  cold('Cold'),
  unknown('Unknown');

  const BatteryHealth(this.label);
  final String label;

  static BatteryHealth parse(Object? key) => values.firstWhere((h) => h.name == key, orElse: () => unknown);
}

/// One live reading from the phone. Built from the map in `Snapshot.kt`.
class BatterySnapshot {
  const BatterySnapshot({
    required this.time,
    required this.level,
    required this.currentMa,
    required this.voltageMv,
    required this.temperatureC,
    required this.plug,
    required this.status,
    required this.health,
    this.cycleCount,
    this.chargeCounterUah,
    this.screenOn = true,
  });

  factory BatterySnapshot.fromMap(Map<Object?, Object?> m) => BatterySnapshot(
    time: DateTime.fromMillisecondsSinceEpoch((m['ts'] as num?)?.toInt() ?? 0),
    level: ((m['level'] as num?)?.toInt() ?? 0).clamp(0, 100),
    currentMa: (m['currentMa'] as num?)?.toDouble(),
    voltageMv: (m['voltageMv'] as num?)?.toInt() ?? 0,
    temperatureC: (m['tempC'] as num?)?.toDouble() ?? 0,
    plug: PlugType.parse(m['plugged']),
    status: ChargeStatus.parse(m['status']),
    health: BatteryHealth.parse(m['health']),
    cycleCount: (m['cycleCount'] as num?)?.toInt(),
    chargeCounterUah: (m['chargeCounterUah'] as num?)?.toInt(),
    screenOn: m['screenOn'] as bool? ?? true,
  );

  final DateTime time;
  final int level;

  /// Milliamperes, + charging and - discharging. Null when the phone has no current sensor.
  final double? currentMa;
  final int voltageMv;
  final double temperatureC;
  final PlugType plug;
  final ChargeStatus status;
  final BatteryHealth health;

  /// Android 14+ only, and only on phones that report it.
  final int? cycleCount;

  /// Charge left in the battery in microampere-hours, used for the time estimates.
  final int? chargeCounterUah;
  final bool screenOn;

  bool get isCharging => status == ChargeStatus.charging;
  bool get pluggedIn => plug != PlugType.none;
  double get volts => voltageMv / 1000;

  /// Power in watts, signed like the current. Null without a current sensor.
  double? get watts => currentMa == null ? null : currentMa! * voltageMv / 1e6;
}
