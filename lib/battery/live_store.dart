import 'dart:async';

import 'package:flutter/foundation.dart';

import 'battery_math.dart';
import 'battery_platform.dart';
import 'battery_snapshot.dart';

/// Live readings for the first screen. Subscribes to the native stream only while [start]ed
/// (the screen is visible), and keeps the last [window] readings for the chart.
class LiveStore extends ChangeNotifier {
  LiveStore(this.platform);

  /// About two minutes at one reading per second.
  static const window = 120;

  /// Readings averaged for the time estimate, so a spike does not make it jump.
  static const smoothing = 30;

  final BatteryPlatform platform;

  StreamSubscription<BatterySnapshot>? _sub;
  final List<BatterySnapshot> _history = [];
  BatterySnapshot? _latest;
  Object? _error;

  BatterySnapshot? get latest => _latest;
  Object? get error => _error;
  bool get active => _sub != null;
  List<BatterySnapshot> get history => List.unmodifiable(_history);

  /// Watts of the last readings, oldest first. Empty without a current sensor.
  List<double> get wattsSeries => [
    for (final s in _history)
      if (s.watts != null) s.watts!,
  ];

  /// Milliamperes of the last readings, oldest first.
  List<double> get currentSeries => [
    for (final s in _history)
      if (s.currentMa != null) s.currentMa!,
  ];

  /// Estimated time to full / to empty, or null when it cannot be told.
  Duration? get timeRemaining {
    final s = _latest;
    if (s == null) return null;
    final recent = currentSeries;
    final tail = recent.length > smoothing ? recent.sublist(recent.length - smoothing) : recent;
    return BatteryMath.timeRemaining(s, smoothedMa: BatteryMath.mean(tail));
  }

  void start() {
    if (_sub != null) return;
    _sub = platform.live().listen(_onReading, onError: _onError);
  }

  void stop() {
    _sub?.cancel();
    _sub = null;
  }

  void _onReading(BatterySnapshot s) {
    _error = null;
    _latest = s;
    _history.add(s);
    if (_history.length > window) _history.removeRange(0, _history.length - window);
    notifyListeners();
  }

  void _onError(Object e) {
    _error = e;
    notifyListeners();
  }

  @override
  void dispose() {
    stop();
    super.dispose();
  }
}
