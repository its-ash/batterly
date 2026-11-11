import 'battery_snapshot.dart';

/// Pure helpers for the live screen: time estimates, unit conversion and number formatting.
abstract final class BatteryMath {
  /// Estimates are meaningless below this current (the battery is basically idle).
  static const minEstimateMa = 30.0;

  /// Longest estimate we show; beyond this the current is too small to trust.
  static const maxEstimate = Duration(hours: 48);

  /// Average of [values], or null when empty.
  static double? mean(Iterable<double> values) {
    if (values.isEmpty) return null;
    return values.reduce((a, b) => a + b) / values.length;
  }

  /// Time until the battery is full (charging) or empty (discharging), from the charge counter
  /// and a smoothed current. Returns null when there is not enough data or the answer is absurd.
  static Duration? timeRemaining(BatterySnapshot s, {double? smoothedMa}) {
    final counterUah = s.chargeCounterUah;
    final ma = smoothedMa ?? s.currentMa;
    if (counterUah == null || ma == null || s.level < 1 || ma.abs() < minEstimateMa) return null;

    final leftMah = counterUah / 1000;
    final capacityMah = leftMah * 100 / s.level;
    final double hours;
    if (s.status == ChargeStatus.charging && ma > 0) {
      if (s.level >= 100) return null;
      hours = (capacityMah - leftMah) / ma;
    } else if (s.status == ChargeStatus.discharging && ma < 0) {
      hours = leftMah / -ma;
    } else {
      return null;
    }
    final d = Duration(seconds: (hours * 3600).round());
    return d > maxEstimate ? null : d;
  }

  /// Estimated full capacity in mAh from the charge counter, or null.
  static double? capacityMah(BatterySnapshot s) {
    final counter = s.chargeCounterUah;
    if (counter == null || s.level < 1) return null;
    return counter / 1000 * 100 / s.level;
  }

  static double celsiusToFahrenheit(double c) => c * 9 / 5 + 32;

  /// "31.5 °C" or "88.7 °F".
  static String temperature(double celsius, {bool fahrenheit = false}) {
    final v = fahrenheit ? celsiusToFahrenheit(celsius) : celsius;
    return '${v.toStringAsFixed(1)} ${fahrenheit ? '°F' : '°C'}';
  }

  /// "+2,450" / "-480" / "0": whole milliamperes with a thousands separator and an explicit sign.
  static String milliAmps(double ma) {
    final rounded = ma.round();
    final sign = rounded > 0 ? '+' : (rounded < 0 ? '-' : '');
    return '$sign${_group(rounded.abs())}';
  }

  /// "+12.4" / "-1.9" / "0.0".
  static String wattsText(double w) {
    final sign = w >= 0.05 ? '+' : (w <= -0.05 ? '-' : '');
    return '$sign${w.abs().toStringAsFixed(1)}';
  }

  /// "1 h 25 min", "42 min" or "< 1 min".
  static String duration(Duration d) {
    final minutes = d.inMinutes;
    if (minutes < 1) return '< 1 min';
    if (minutes < 60) return '$minutes min';
    final h = minutes ~/ 60;
    final m = minutes % 60;
    return m == 0 ? '$h h' : '$h h $m min';
  }

  static String _group(int n) {
    final s = n.toString();
    final buf = StringBuffer();
    for (var i = 0; i < s.length; i++) {
      if (i > 0 && (s.length - i) % 3 == 0) buf.write(',');
      buf.write(s[i]);
    }
    return buf.toString();
  }
}
