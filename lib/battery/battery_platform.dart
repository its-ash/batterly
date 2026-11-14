import 'package:flutter/services.dart';

import 'battery_snapshot.dart';
import 'health.dart';
import 'history_analysis.dart';

/// Small icon of the live notification.
enum StatusIconMode {
  icon('Icon'),
  level('Level %'),
  watts('Watts');

  const StatusIconMode(this.label);
  final String label;
}

/// Settings kept natively (shared with the background service). See `BatteryPrefs.kt`.
class BatteryConfig {
  const BatteryConfig({
    this.monitor = false,
    this.running = false,
    this.chargeAlarm = true,
    this.chargeLimit = 80,
    this.lowReminder = false,
    this.lowLevel = 20,
    this.vibrate = true,
    this.fahrenheit = false,
    this.statusIcon = StatusIconMode.icon,
    this.btAlert = false,
    this.btLowLevel = 20,
  });

  factory BatteryConfig.fromMap(Map<Object?, Object?> m) => BatteryConfig(
    monitor: m['monitor'] as bool? ?? false,
    running: m['running'] as bool? ?? false,
    chargeAlarm: m['chargeAlarm'] as bool? ?? true,
    chargeLimit: (m['chargeLimit'] as num?)?.toInt() ?? 80,
    lowReminder: m['lowReminder'] as bool? ?? false,
    lowLevel: (m['lowLevel'] as num?)?.toInt() ?? 20,
    vibrate: m['vibrate'] as bool? ?? true,
    fahrenheit: m['tempUnit'] == 'f',
    statusIcon: StatusIconMode.values.asNameMap()[m['statusIcon']] ?? StatusIconMode.icon,
    btAlert: m['btAlert'] as bool? ?? false,
    btLowLevel: (m['btLowLevel'] as num?)?.toInt() ?? 20,
  );

  BatteryConfig copyWith({
    bool? monitor,
    bool? chargeAlarm,
    int? chargeLimit,
    bool? lowReminder,
    int? lowLevel,
    bool? vibrate,
    bool? fahrenheit,
    StatusIconMode? statusIcon,
    bool? btAlert,
    int? btLowLevel,
  }) => BatteryConfig(
    monitor: monitor ?? this.monitor,
    running: running,
    chargeAlarm: chargeAlarm ?? this.chargeAlarm,
    chargeLimit: chargeLimit ?? this.chargeLimit,
    lowReminder: lowReminder ?? this.lowReminder,
    lowLevel: lowLevel ?? this.lowLevel,
    vibrate: vibrate ?? this.vibrate,
    fahrenheit: fahrenheit ?? this.fahrenheit,
    statusIcon: statusIcon ?? this.statusIcon,
    btAlert: btAlert ?? this.btAlert,
    btLowLevel: btLowLevel ?? this.btLowLevel,
  );

  final bool monitor;
  final bool running;
  final bool chargeAlarm;
  final int chargeLimit;
  final bool lowReminder;
  final int lowLevel;
  final bool vibrate;
  final bool fahrenheit;
  final StatusIconMode statusIcon;

  /// Low-battery alert for Bluetooth devices at [btLowLevel] percent.
  final bool btAlert;
  final int btLowLevel;
}

/// What the phone can do and which permissions are missing.
class PlatformStatus {
  const PlatformStatus({
    this.notifications = true,
    this.batteryOptimizationsIgnored = true,
    this.hasCurrentSensor = true,
    this.bluetooth = true,
    this.sdk = 34,
  });

  factory PlatformStatus.fromMap(Map<Object?, Object?> m) => PlatformStatus(
    notifications: m['notifications'] as bool? ?? true,
    batteryOptimizationsIgnored: m['batteryOptimizationsIgnored'] as bool? ?? true,
    hasCurrentSensor: m['hasCurrentSensor'] as bool? ?? true,
    bluetooth: m['bluetooth'] as bool? ?? true,
    sdk: (m['sdk'] as num?)?.toInt() ?? 34,
  );

  final bool notifications;
  final bool batteryOptimizationsIgnored;
  final bool hasCurrentSensor;

  /// Bluetooth permission (Android 12+ `BLUETOOTH_CONNECT`) granted.
  final bool bluetooth;

  /// Android API level.
  final int sdk;
}

/// The native side, so stores and screens can be tested with a fake.
abstract interface class BatteryPlatform {
  /// A reading every second while subscribed.
  Stream<BatterySnapshot> live();
  Future<BatteryConfig> getConfig();
  Future<void> setConfig(String key, Object value);
  Future<PlatformStatus> status();

  /// [name]: `notifications`, `battery` or `bluetooth`.
  Future<void> openPermission(String name);
  Future<List<HistorySample>> samples(DateTime since);
  Future<void> clearHistory();
  Future<HealthInfo> health();
  Future<List<BtDevice>> bluetoothDevices();
}

/// Talks to `MainActivity.kt` over `com.itsash.batterly/control` and `.../live`.
class MethodChannelBatteryPlatform implements BatteryPlatform {
  MethodChannelBatteryPlatform({MethodChannel? control, EventChannel? liveChannel})
    : _control = control ?? const MethodChannel('com.itsash.batterly/control'),
      _live = liveChannel ?? const EventChannel('com.itsash.batterly/live');

  final MethodChannel _control;
  final EventChannel _live;

  @override
  Stream<BatterySnapshot> live() =>
      _live.receiveBroadcastStream().map((e) => BatterySnapshot.fromMap(e as Map<Object?, Object?>));

  @override
  Future<BatteryConfig> getConfig() async =>
      BatteryConfig.fromMap(await _control.invokeMapMethod<Object?, Object?>('getConfig') ?? const {});

  @override
  Future<void> setConfig(String key, Object value) => _control.invokeMethod('setConfig', {'key': key, 'value': value});

  @override
  Future<PlatformStatus> status() async =>
      PlatformStatus.fromMap(await _control.invokeMapMethod<Object?, Object?>('status') ?? const {});

  @override
  Future<void> openPermission(String name) => _control.invokeMethod('openPermission', {'name': name});

  @override
  Future<List<HistorySample>> samples(DateTime since) async {
    final rows = await _control.invokeListMethod<Map<Object?, Object?>>('samples', {
      'sinceMs': since.millisecondsSinceEpoch,
    });
    return [for (final r in rows ?? const <Map<Object?, Object?>>[]) HistorySample.fromMap(r)];
  }

  @override
  Future<void> clearHistory() => _control.invokeMethod('clearHistory');

  @override
  Future<HealthInfo> health() async =>
      HealthInfo.fromMap(await _control.invokeMapMethod<Object?, Object?>('health') ?? const {});

  @override
  Future<List<BtDevice>> bluetoothDevices() async {
    final rows = await _control.invokeListMethod<Map<Object?, Object?>>('bluetoothDevices');
    return [for (final r in rows ?? const <Map<Object?, Object?>>[]) BtDevice.fromMap(r)];
  }
}
