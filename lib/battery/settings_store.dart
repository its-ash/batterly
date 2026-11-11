import 'package:flutter/foundation.dart';

import 'battery_platform.dart';

/// Settings and permission state shared by the home and settings screens.
class SettingsStore extends ChangeNotifier {
  SettingsStore(this.platform);

  final BatteryPlatform platform;

  BatteryConfig config = const BatteryConfig();
  PlatformStatus status = const PlatformStatus();
  bool loaded = false;

  /// Reads the native settings and permission state. Call again after returning from system settings.
  Future<void> load() async {
    try {
      config = await platform.getConfig();
      status = await platform.status();
    } catch (e) {
      debugPrint('Batterly: could not load settings: $e');
    }
    loaded = true;
    notifyListeners();
  }

  /// Shows [next] right away (sliders must follow the finger) and saves it natively. Switching the
  /// monitor re-reads the state afterwards to learn whether the service is running.
  Future<void> _set(String key, Object value, BatteryConfig next) async {
    config = next;
    notifyListeners();
    await platform.setConfig(key, value);
    if (key == 'monitor') {
      config = await platform.getConfig();
      notifyListeners();
    }
  }

  /// Turns the background monitor on or off. Asks for the notification permission first when
  /// turning it on; the monitor still runs without it, only its notification stays hidden.
  Future<void> setMonitor(bool on) async {
    if (on && !status.notifications) await platform.openPermission('notifications');
    await _set('monitor', on, config.copyWith(monitor: on));
  }

  Future<void> setChargeAlarm(bool on) => _set('chargeAlarm', on, config.copyWith(chargeAlarm: on));
  Future<void> setChargeLimit(int percent) => _set('chargeLimit', percent, config.copyWith(chargeLimit: percent));
  Future<void> setLowReminder(bool on) => _set('lowReminder', on, config.copyWith(lowReminder: on));
  Future<void> setLowLevel(int percent) => _set('lowLevel', percent, config.copyWith(lowLevel: percent));
  Future<void> setVibrate(bool on) => _set('vibrate', on, config.copyWith(vibrate: on));
  Future<void> setFahrenheit(bool on) => _set('tempUnit', on ? 'f' : 'c', config.copyWith(fahrenheit: on));

  Future<void> openPermission(String name) => platform.openPermission(name);
}
