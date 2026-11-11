import 'dart:async';

import 'package:batterly/battery/battery_platform.dart';
import 'package:batterly/battery/battery_snapshot.dart';
import 'package:batterly/battery/history_analysis.dart';
import 'package:batterly/battery/live_store.dart';
import 'package:batterly/battery/settings_store.dart';
import 'package:flutter_test/flutter_test.dart';

class FakePlatform implements BatteryPlatform {
  final controller = StreamController<BatterySnapshot>.broadcast();
  final calls = <String>[];
  BatteryConfig config = const BatteryConfig();
  PlatformStatus statusValue = const PlatformStatus(notifications: false);

  @override
  Stream<BatterySnapshot> live() => controller.stream;

  @override
  Future<BatteryConfig> getConfig() async => config;

  @override
  Future<void> setConfig(String key, Object value) async {
    calls.add('set $key=$value');
    if (key == 'monitor') config = config.copyWith(monitor: value as bool);
  }

  @override
  Future<PlatformStatus> status() async => statusValue;

  @override
  Future<void> openPermission(String name) async => calls.add('open $name');

  @override
  Future<List<HistorySample>> samples(DateTime since) async => const [];

  @override
  Future<void> clearHistory() async {}
}

BatterySnapshot reading(int i, {double ma = 1000}) => BatterySnapshot(
  time: DateTime.fromMillisecondsSinceEpoch(i * 1000),
  level: 50,
  currentMa: ma,
  voltageMv: 4000,
  temperatureC: 30,
  plug: PlugType.ac,
  status: ChargeStatus.charging,
  health: BatteryHealth.good,
  chargeCounterUah: 2500000,
);

void main() {
  group('LiveStore', () {
    test('subscribes on start, keeps a two-minute window, unsubscribes on stop', () async {
      final p = FakePlatform();
      final store = LiveStore(p);
      expect(p.controller.hasListener, isFalse);

      store.start();
      expect(p.controller.hasListener, isTrue);
      expect(store.active, isTrue);

      for (var i = 0; i < LiveStore.window + 30; i++) {
        p.controller.add(reading(i));
      }
      await Future<void>.delayed(Duration.zero);
      expect(store.history, hasLength(LiveStore.window));
      expect(store.latest!.time, DateTime.fromMillisecondsSinceEpoch((LiveStore.window + 29) * 1000));
      expect(store.wattsSeries.first, closeTo(4, 1e-9));

      store.stop();
      expect(p.controller.hasListener, isFalse);
      expect(store.active, isFalse);
    });

    test('start is idempotent', () {
      final p = FakePlatform();
      final store = LiveStore(p)..start()..start();
      expect(p.controller.hasListener, isTrue);
      store.dispose();
      expect(p.controller.hasListener, isFalse);
    });

    test('time estimate uses the smoothed current', () async {
      final p = FakePlatform();
      final store = LiveStore(p)..start();
      // Wild first readings fall out of the 30-sample smoothing window.
      for (var i = 0; i < 10; i++) {
        p.controller.add(reading(i, ma: 20000));
      }
      for (var i = 10; i < 50; i++) {
        p.controller.add(reading(i, ma: 2500));
      }
      await Future<void>.delayed(Duration.zero);
      expect(store.timeRemaining, const Duration(hours: 1));
      store.dispose();
    });

    test('a stream error is exposed and cleared by the next reading', () async {
      final p = FakePlatform();
      final store = LiveStore(p)..start();
      p.controller.addError(StateError('boom'));
      await Future<void>.delayed(Duration.zero);
      expect(store.error, isA<StateError>());
      p.controller.add(reading(1));
      await Future<void>.delayed(Duration.zero);
      expect(store.error, isNull);
      store.dispose();
    });
  });

  group('SettingsStore', () {
    test('load reads config and status', () async {
      final store = SettingsStore(FakePlatform());
      await store.load();
      expect(store.loaded, isTrue);
      expect(store.status.notifications, isFalse);
    });

    test('turning the monitor on asks for notifications first when missing', () async {
      final p = FakePlatform();
      final store = SettingsStore(p);
      await store.load();
      await store.setMonitor(true);
      expect(p.calls, ['open notifications', 'set monitor=true']);
      expect(store.config.monitor, isTrue);
    });

    test('settings are applied optimistically and sent by key', () async {
      final p = FakePlatform();
      final store = SettingsStore(p);
      final future = store.setChargeLimit(90);
      expect(store.config.chargeLimit, 90);
      await future;
      await store.setFahrenheit(true);
      expect(store.config.fahrenheit, isTrue);
      expect(p.calls, ['set chargeLimit=90', 'set tempUnit=f']);
    });
  });

  test('BatteryConfig parses the channel map', () {
    final c = BatteryConfig.fromMap({
      'monitor': true,
      'chargeLimit': 85,
      'lowReminder': true,
      'lowLevel': 15,
      'tempUnit': 'f',
      'vibrate': false,
    });
    expect(c.monitor, isTrue);
    expect(c.chargeLimit, 85);
    expect(c.lowLevel, 15);
    expect(c.fahrenheit, isTrue);
    expect(c.vibrate, isFalse);
    expect(c.chargeAlarm, isTrue); // default
  });
}
