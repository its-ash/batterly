/// Battery health from the native `health` call (see `HealthLogic.kt`, `DeviceBattery.kt`) and
/// Bluetooth device batteries. The getters here are pure and tested in `test/health_test.dart`.
library;

/// One capacity measurement: a charge or discharge of at least 20% measured with the charge counter.
class CapacityEstimate {
  const CapacityEstimate({
    required this.time,
    required this.mah,
    required this.fromLevel,
    required this.toLevel,
    required this.charging,
  });

  factory CapacityEstimate.fromMap(Map<Object?, Object?> m) => CapacityEstimate(
    time: DateTime.fromMillisecondsSinceEpoch((m['ts'] as num).toInt()),
    mah: (m['mah'] as num).toDouble(),
    fromLevel: (m['fromLevel'] as num).toInt(),
    toLevel: (m['toLevel'] as num).toInt(),
    charging: m['charging'] as bool? ?? true,
  );

  final DateTime time;
  final double mah;
  final int fromLevel;
  final int toLevel;
  final bool charging;
}

enum HealthGrade {
  excellent('Excellent'),
  good('Good'),
  fair('Fair'),
  worn('Worn');

  const HealthGrade(this.label);
  final String label;

  static HealthGrade of(double percent) => switch (percent) {
    >= 90 => excellent,
    >= 80 => good,
    >= 70 => fair,
    _ => worn,
  };
}

class HealthInfo {
  const HealthInfo({
    this.designMah,
    this.estimates = const [],
    this.chargedPercent = 0,
    this.cycleSince,
    this.cycleCount,
    this.statsGranted = false,
    this.stateOfHealth,
    this.manufactureDate,
    this.firstUseDate,
    this.chargingPolicy,
  });

  factory HealthInfo.fromMap(Map<Object?, Object?> m) {
    DateTime? date(Object? v) {
      final ms = (v as num?)?.toInt() ?? 0;
      return ms > 0 ? DateTime.fromMillisecondsSinceEpoch(ms) : null;
    }

    return HealthInfo(
      designMah: (m['designMah'] as num?)?.toDouble(),
      estimates: [
        for (final e in (m['estimates'] as List<Object?>? ?? const []))
          CapacityEstimate.fromMap(e! as Map<Object?, Object?>),
      ],
      chargedPercent: (m['chargedPercent'] as num?)?.toInt() ?? 0,
      cycleSince: date(m['cycleSinceMs']),
      cycleCount: (m['cycleCount'] as num?)?.toInt(),
      statsGranted: m['statsGranted'] as bool? ?? false,
      stateOfHealth: (m['stateOfHealth'] as num?)?.toInt(),
      manufactureDate: date(m['manufactureDateMs']),
      firstUseDate: date(m['firstUseDateMs']),
      chargingPolicy: (m['chargingPolicy'] as num?)?.toInt(),
    );
  }

  /// How many of the newest estimates the capacity is the median of.
  static const window = 5;

  /// Vendor design capacity, null when the phone hides it.
  final double? designMah;

  /// Oldest first.
  final List<CapacityEstimate> estimates;

  /// Every percent charged while the monitor watched. 100 = one full cycle.
  final int chargedPercent;
  final DateTime? cycleSince;

  /// Reported by Android 14+ (no permission needed), null otherwise.
  final int? cycleCount;

  /// `BATTERY_STATS` granted with adb (Android 14+ extras below).
  final bool statsGranted;
  final int? stateOfHealth;
  final DateTime? manufactureDate;
  final DateTime? firstUseDate;

  /// 1 default, 2 adaptive (always on), 3 adaptive (AC), 4 long life.
  final int? chargingPolicy;

  /// Median of the newest [window] estimates, or null when there are none.
  double? get estimatedMah {
    if (estimates.isEmpty) return null;
    final recent = estimates.skip(estimates.length > window ? estimates.length - window : 0).map((e) => e.mah).toList()
      ..sort();
    final mid = recent.length ~/ 2;
    return recent.length.isOdd ? recent[mid] : (recent[mid - 1] + recent[mid]) / 2;
  }

  /// Estimated capacity as a percent of the design capacity (capped at 100), or null.
  double? get healthPercent {
    final est = estimatedMah;
    final design = designMah;
    if (est == null || design == null || design <= 0) return null;
    final p = est / design * 100;
    return p > 100 ? 100 : p;
  }

  double get estimatedCycles => chargedPercent / 100;

  String? get chargingPolicyLabel => switch (chargingPolicy) {
    1 => 'Default',
    2 || 3 => 'Adaptive charging',
    4 => 'Long life (charge limit)',
    null => null,
    _ => 'Policy $chargingPolicy',
  };
}

enum BtKind { headphones, watch, keyboard, mouse, phone, computer, car, other }

/// A paired Bluetooth device. [level] is null when it is not connected or does not report a level.
class BtDevice {
  const BtDevice({required this.address, required this.name, required this.kind, required this.connected, this.level});

  factory BtDevice.fromMap(Map<Object?, Object?> m) => BtDevice(
    address: m['address'] as String? ?? '',
    name: m['name'] as String? ?? 'Unknown device',
    kind: BtKind.values.asNameMap()[m['kind']] ?? BtKind.other,
    connected: m['connected'] as bool? ?? false,
    level: (m['level'] as num?)?.toInt(),
  );

  final String address;
  final String name;
  final BtKind kind;
  final bool connected;
  final int? level;
}
