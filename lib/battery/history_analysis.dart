import 'battery_snapshot.dart';

/// One logged minute from the background monitor (rows of `HistoryDb.kt`).
class HistorySample {
  const HistorySample({
    required this.time,
    required this.level,
    required this.plug,
    this.currentMa,
    this.voltageMv = 0,
    this.temperatureC = 0,
    this.screenOn = true,
  });

  factory HistorySample.fromMap(Map<Object?, Object?> m) => HistorySample(
    time: DateTime.fromMillisecondsSinceEpoch((m['ts'] as num).toInt()),
    level: (m['level'] as num).toInt().clamp(0, 100),
    currentMa: (m['currentMa'] as num?)?.toDouble(),
    voltageMv: (m['voltageMv'] as num?)?.toInt() ?? 0,
    temperatureC: (m['tempC'] as num?)?.toDouble() ?? 0,
    plug: PlugType.parse(m['plugged']),
    screenOn: m['screenOn'] as bool? ?? true,
  );

  final DateTime time;
  final int level;
  final double? currentMa;
  final int voltageMv;
  final double temperatureC;
  final PlugType plug;
  final bool screenOn;

  bool get pluggedIn => plug != PlugType.none;
  double? get watts => currentMa == null ? null : currentMa! * voltageMv / 1e6;
}

/// A stretch of time on a charger.
class ChargeSession {
  const ChargeSession({
    required this.start,
    required this.end,
    required this.startLevel,
    required this.endLevel,
    required this.plug,
    this.averageWatts,
    this.peakWatts,
  });

  final DateTime start;
  final DateTime end;
  final int startLevel;
  final int endLevel;
  final PlugType plug;

  /// Mean power over the samples that were actually charging. Null when none were.
  final double? averageWatts;
  final double? peakWatts;

  Duration get duration => end.difference(start);
  int get gained => endLevel - startLevel;
}

/// Drain speed in percent per hour, over the time it was measured.
class DrainRate {
  const DrainRate({required this.percent, required this.hours});

  final double percent;
  final double hours;

  double? get perHour => hours > 0 ? percent / hours : null;
  Duration get measured => Duration(seconds: (hours * 3600).round());
}

class DrainAnalysis {
  const DrainAnalysis({
    required this.overall,
    required this.screenOn,
    required this.screenOff,
    required this.byHour,
    required this.worstHours,
  });

  static const empty = DrainAnalysis(
    overall: DrainRate(percent: 0, hours: 0),
    screenOn: DrainRate(percent: 0, hours: 0),
    screenOff: DrainRate(percent: 0, hours: 0),
    byHour: [],
    worstHours: [],
  );

  final DrainRate overall;
  final DrainRate screenOn;
  final DrainRate screenOff;

  /// 24 entries, index = hour of the day (local time). Hours without data have 0 hours measured.
  final List<DrainRate> byHour;

  /// Hours of the day (0-23) with the highest drain, worst first.
  final List<int> worstHours;

  bool get hasData => overall.hours > 0;
}

/// Pure analysis of the logged samples. Covered by `history_analysis_test.dart`.
abstract final class HistoryAnalysis {
  /// Samples further apart than this are not joined: the monitor was not running in between.
  static const maxGap = Duration(minutes: 10);

  /// An hour needs at least this much measured time to be ranked as "worst".
  static const minHourMeasured = 10.0 / 60;

  static const worstCount = 3;

  /// Charging sessions, oldest first. A session is consecutive plugged-in samples; it ends when
  /// the charger is removed or the samples stop for longer than [maxGap].
  static List<ChargeSession> sessions(List<HistorySample> samples) {
    final out = <ChargeSession>[];
    final run = <HistorySample>[];

    void close() {
      if (run.length >= 2) out.add(_session(run));
      run.clear();
    }

    for (final s in samples) {
      if (!s.pluggedIn) {
        close();
        continue;
      }
      if (run.isNotEmpty && s.time.difference(run.last.time) > maxGap) close();
      run.add(s);
    }
    close();
    return out;
  }

  static ChargeSession _session(List<HistorySample> run) {
    final charging = [
      for (final s in run)
        if (s.watts != null && s.watts! > 0) s.watts!,
    ];
    return ChargeSession(
      start: run.first.time,
      end: run.last.time,
      startLevel: run.first.level,
      endLevel: run.last.level,
      plug: run.first.plug,
      averageWatts: charging.isEmpty ? null : charging.reduce((a, b) => a + b) / charging.length,
      peakWatts: charging.isEmpty ? null : charging.reduce((a, b) => a > b ? a : b),
    );
  }

  /// Drain rates from every pair of neighbouring samples where the phone was on battery.
  /// The drop in a pair counts for the hour of the first sample and its screen state.
  static DrainAnalysis drain(List<HistorySample> samples) {
    final hourPercent = List.filled(24, 0.0);
    final hourHours = List.filled(24, 0.0);
    var onPercent = 0.0, onHours = 0.0, offPercent = 0.0, offHours = 0.0;

    for (var i = 0; i + 1 < samples.length; i++) {
      final a = samples[i], b = samples[i + 1];
      if (a.pluggedIn || b.pluggedIn) continue;
      final dt = b.time.difference(a.time);
      if (dt <= Duration.zero || dt > maxGap) continue;

      final hours = dt.inMilliseconds / 3600000;
      // Level can tick up by one on a noisy reading; that is not charging, count it as no drain.
      final drop = (a.level - b.level).clamp(0, 100).toDouble();

      hourPercent[a.time.hour] += drop;
      hourHours[a.time.hour] += hours;
      if (a.screenOn) {
        onPercent += drop;
        onHours += hours;
      } else {
        offPercent += drop;
        offHours += hours;
      }
    }

    final totalHours = onHours + offHours;
    if (totalHours <= 0) return DrainAnalysis.empty;

    final byHour = [for (var h = 0; h < 24; h++) DrainRate(percent: hourPercent[h], hours: hourHours[h])];
    final ranked = [
      for (var h = 0; h < 24; h++)
        if (byHour[h].hours >= minHourMeasured && (byHour[h].perHour ?? 0) > 0) h,
    ]..sort((x, y) => byHour[y].perHour!.compareTo(byHour[x].perHour!));

    return DrainAnalysis(
      overall: DrainRate(percent: onPercent + offPercent, hours: totalHours),
      screenOn: DrainRate(percent: onPercent, hours: onHours),
      screenOff: DrainRate(percent: offPercent, hours: offHours),
      byHour: byHour,
      worstHours: ranked.take(worstCount).toList(),
    );
  }

  /// Samples from the last [range] before [now].
  static List<HistorySample> within(List<HistorySample> samples, Duration range, {DateTime? now}) {
    final from = (now ?? DateTime.now()).subtract(range);
    return [
      for (final s in samples)
        if (!s.time.isBefore(from)) s,
    ];
  }

  /// At most [maxPoints] level values for a chart: each bucket of neighbouring samples is averaged.
  static List<double> levelSeries(List<HistorySample> samples, {int maxPoints = 96}) {
    if (samples.length <= maxPoints) return [for (final s in samples) s.level.toDouble()];
    final out = <double>[];
    final size = samples.length / maxPoints;
    for (var i = 0; i < maxPoints; i++) {
      final from = (i * size).floor();
      final to = ((i + 1) * size).floor().clamp(from + 1, samples.length);
      var sum = 0;
      for (var j = from; j < to; j++) {
        sum += samples[j].level;
      }
      out.add(sum / (to - from));
    }
    return out;
  }
}
