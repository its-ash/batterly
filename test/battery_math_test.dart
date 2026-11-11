import 'package:batterly/battery/battery_math.dart';
import 'package:batterly/battery/battery_snapshot.dart';
import 'package:flutter_test/flutter_test.dart';

BatterySnapshot snap({
  int level = 50,
  double? ma = 2000,
  int mv = 4000,
  ChargeStatus status = ChargeStatus.charging,
  int? counterUah = 2500000,
}) => BatterySnapshot(
  time: DateTime(2026),
  level: level,
  currentMa: ma,
  voltageMv: mv,
  temperatureC: 30,
  plug: status == ChargeStatus.charging ? PlugType.ac : PlugType.none,
  status: status,
  health: BatteryHealth.good,
  chargeCounterUah: counterUah,
);

void main() {
  group('timeRemaining', () {
    test('time to full from the charge counter', () {
      // 2500 mAh left at 50 % -> 5000 mAh capacity, 2500 mAh to go at 2500 mA = 1 h.
      final d = BatteryMath.timeRemaining(snap(ma: 2500));
      expect(d, const Duration(hours: 1));
    });

    test('uses the smoothed current when given', () {
      final d = BatteryMath.timeRemaining(snap(ma: 9000), smoothedMa: 1250);
      expect(d, const Duration(hours: 2));
    });

    test('time to empty while discharging', () {
      // 2500 mAh left at 500 mA = 5 h.
      final d = BatteryMath.timeRemaining(snap(status: ChargeStatus.discharging, ma: -500));
      expect(d, const Duration(hours: 5));
    });

    test('null when the answer cannot be trusted', () {
      expect(BatteryMath.timeRemaining(snap(counterUah: null)), isNull);
      expect(BatteryMath.timeRemaining(snap(ma: null)), isNull);
      expect(BatteryMath.timeRemaining(snap(ma: 10)), isNull); // idle
      expect(BatteryMath.timeRemaining(snap(level: 100)), isNull); // already full
      expect(BatteryMath.timeRemaining(snap(level: 0)), isNull);
      expect(BatteryMath.timeRemaining(snap(status: ChargeStatus.full)), isNull);
      // Charging with a negative current contradicts the status.
      expect(BatteryMath.timeRemaining(snap(ma: -800)), isNull);
      // More than 48 h away.
      expect(BatteryMath.timeRemaining(snap(status: ChargeStatus.discharging, ma: -40)), isNull);
    });

    test('capacity estimate', () {
      expect(BatteryMath.capacityMah(snap()), closeTo(5000, 1e-9));
      expect(BatteryMath.capacityMah(snap(counterUah: null)), isNull);
    });
  });

  group('formatting', () {
    test('milliAmps', () {
      expect(BatteryMath.milliAmps(2450.4), '+2,450');
      expect(BatteryMath.milliAmps(-480), '-480');
      expect(BatteryMath.milliAmps(-1234567), '-1,234,567');
      expect(BatteryMath.milliAmps(0.2), '0');
    });

    test('wattsText', () {
      expect(BatteryMath.wattsText(12.44), '+12.4');
      expect(BatteryMath.wattsText(-1.86), '-1.9');
      expect(BatteryMath.wattsText(0.01), '0.0');
    });

    test('temperature', () {
      expect(BatteryMath.temperature(31.54), '31.5 °C');
      expect(BatteryMath.temperature(30, fahrenheit: true), '86.0 °F');
    });

    test('duration', () {
      expect(BatteryMath.duration(const Duration(seconds: 20)), '< 1 min');
      expect(BatteryMath.duration(const Duration(minutes: 42)), '42 min');
      expect(BatteryMath.duration(const Duration(hours: 2)), '2 h');
      expect(BatteryMath.duration(const Duration(minutes: 85)), '1 h 25 min');
    });

    test('mean', () {
      expect(BatteryMath.mean(const []), isNull);
      expect(BatteryMath.mean([1, 2, 6]), 3);
    });
  });

  group('snapshot', () {
    test('parses the channel map and computes watts', () {
      final s = BatterySnapshot.fromMap({
        'ts': 1000,
        'level': 63,
        'currentMa': 2450,
        'voltageMv': 4200,
        'tempC': 33.2,
        'plugged': 'usb',
        'status': 'charging',
        'health': 'overheat',
        'cycleCount': 120,
        'chargeCounterUah': 3000000,
        'screenOn': false,
      });
      expect(s.level, 63);
      expect(s.plug, PlugType.usb);
      expect(s.isCharging, isTrue);
      expect(s.health, BatteryHealth.overheat);
      expect(s.cycleCount, 120);
      expect(s.watts, closeTo(10.29, 1e-9));
      expect(s.volts, 4.2);
    });

    test('unknown or missing values fall back safely', () {
      final s = BatterySnapshot.fromMap({'plugged': 'plasma', 'status': 'x', 'health': 'y'});
      expect(s.plug, PlugType.none);
      expect(s.status, ChargeStatus.unknown);
      expect(s.health, BatteryHealth.unknown);
      expect(s.currentMa, isNull);
      expect(s.watts, isNull);
    });
  });
}
