import 'package:batterly/battery/health.dart';
import 'package:flutter_test/flutter_test.dart';

Map<String, Object?> est(int day, double mah) => {
  'ts': DateTime(2026, 11, day).millisecondsSinceEpoch,
  'mah': mah,
  'fromLevel': 30,
  'toLevel': 80,
  'charging': true,
};

void main() {
  test('no estimates: no capacity, no health', () {
    const info = HealthInfo(designMah: 5000);
    expect(info.estimatedMah, isNull);
    expect(info.healthPercent, isNull);
  });

  test('capacity is the median of the newest five estimates', () {
    final info = HealthInfo.fromMap({
      'designMah': 5000,
      'estimates': [
        est(1, 1000), // old outlier, outside the window
        est(2, 4600),
        est(3, 4700),
        est(4, 9000), // outlier inside the window: the median ignores it
        est(5, 4650),
        est(6, 4500),
      ],
    });
    expect(info.estimatedMah, 4650);
    expect(info.healthPercent, closeTo(93, 1e-9));
    expect(HealthGrade.of(info.healthPercent!), HealthGrade.excellent);
  });

  test('even count averages the middle two; health is capped at 100', () {
    final info = HealthInfo.fromMap({
      'designMah': 4000,
      'estimates': [est(1, 4200), est(2, 4400)],
    });
    expect(info.estimatedMah, 4300);
    expect(info.healthPercent, 100);
  });

  test('without a design capacity there is a capacity but no health %', () {
    final info = HealthInfo.fromMap({
      'estimates': [est(1, 4200)],
    });
    expect(info.estimatedMah, 4200);
    expect(info.healthPercent, isNull);
  });

  test('grades', () {
    expect(HealthGrade.of(85), HealthGrade.good);
    expect(HealthGrade.of(72), HealthGrade.fair);
    expect(HealthGrade.of(60), HealthGrade.worn);
  });

  test('cycles, dates and advanced values parse', () {
    final info = HealthInfo.fromMap({
      'chargedPercent': 1250,
      'cycleSinceMs': DateTime(2026, 11, 1).millisecondsSinceEpoch,
      'cycleCount': 312,
      'statsGranted': true,
      'stateOfHealth': 91,
      'manufactureDateMs': DateTime(2024, 3, 5).millisecondsSinceEpoch,
      'firstUseDateMs': 0,
      'chargingPolicy': 4,
    });
    expect(info.estimatedCycles, 12.5);
    expect(info.cycleSince, DateTime(2026, 11, 1));
    expect(info.cycleCount, 312);
    expect(info.stateOfHealth, 91);
    expect(info.manufactureDate, DateTime(2024, 3, 5));
    expect(info.firstUseDate, isNull);
    expect(info.chargingPolicyLabel, 'Long life (charge limit)');
  });

  test('Bluetooth device parses, unknown kind falls back', () {
    final d = BtDevice.fromMap({'address': 'AA', 'name': 'Buds', 'kind': 'headphones', 'connected': true, 'level': 40});
    expect(d.kind, BtKind.headphones);
    expect(d.level, 40);
    expect(BtDevice.fromMap({'kind': 'toaster'}).kind, BtKind.other);
  });
}
