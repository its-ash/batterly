import 'package:batterly/battery/battery_snapshot.dart';
import 'package:batterly/battery/history_analysis.dart';
import 'package:flutter_test/flutter_test.dart';

final _t0 = DateTime(2026, 3, 10, 9, 0);

HistorySample sample(
  int minute,
  int level, {
  PlugType plug = PlugType.none,
  double? ma,
  int mv = 4000,
  bool screen = true,
}) => HistorySample(
  time: _t0.add(Duration(minutes: minute)),
  level: level,
  plug: plug,
  currentMa: ma,
  voltageMv: mv,
  screenOn: screen,
);

void main() {
  group('sessions', () {
    test('groups consecutive plugged-in samples with avg and peak watts', () {
      final samples = [
        sample(0, 50),
        sample(1, 50, plug: PlugType.ac, ma: 2000), // 8 W
        sample(2, 52, plug: PlugType.ac, ma: 3000), // 12 W
        sample(3, 54, plug: PlugType.ac, ma: 1000), // 4 W
        sample(4, 55),
      ];
      final s = HistoryAnalysis.sessions(samples);
      expect(s, hasLength(1));
      expect(s.single.startLevel, 50);
      expect(s.single.endLevel, 54);
      expect(s.single.gained, 4);
      expect(s.single.duration, const Duration(minutes: 2));
      expect(s.single.averageWatts, closeTo(8, 1e-9));
      expect(s.single.peakWatts, closeTo(12, 1e-9));
      expect(s.single.plug, PlugType.ac);
    });

    test('a gap longer than ten minutes splits a session', () {
      final samples = [
        sample(0, 20, plug: PlugType.usb, ma: 500),
        sample(1, 21, plug: PlugType.usb, ma: 500),
        sample(30, 40, plug: PlugType.usb, ma: 500),
        sample(31, 41, plug: PlugType.usb, ma: 500),
      ];
      expect(HistoryAnalysis.sessions(samples), hasLength(2));
    });

    test('a lone plugged-in sample is not a session', () {
      expect(HistoryAnalysis.sessions([sample(0, 50, plug: PlugType.usb, ma: 500)]), isEmpty);
    });

    test('watts are null when nothing was charging', () {
      final s = HistoryAnalysis.sessions([
        sample(0, 100, plug: PlugType.ac, ma: 0),
        sample(1, 100, plug: PlugType.ac, ma: -5),
      ]);
      expect(s.single.averageWatts, isNull);
      expect(s.single.peakWatts, isNull);
    });
  });

  group('drain', () {
    test('computes percent per hour overall', () {
      // 60 minutes, 6 % lost.
      final samples = [for (var m = 0; m <= 60; m += 5) sample(m, 80 - (m ~/ 10))];
      final d = HistoryAnalysis.drain(samples);
      expect(d.hasData, isTrue);
      expect(d.overall.perHour, closeTo(6, 1e-9));
      expect(d.overall.measured, const Duration(hours: 1));
    });

    test('separates screen on and off', () {
      final samples = [
        sample(0, 90, screen: true),
        sample(5, 88, screen: true), // 2 % in 5 min on
        sample(10, 88, screen: false),
        sample(15, 88, screen: false), // 0 % in 5 min off
        sample(20, 88),
      ];
      final d = HistoryAnalysis.drain(samples);
      expect(d.screenOn.percent, 2);
      expect(d.screenOff.percent, 0);
      expect(d.screenOn.perHour, closeTo(2 / (10 / 60), 1e-9));
      expect(d.screenOff.perHour, 0);
    });

    test('ignores charging and long gaps, and never counts a level rise as drain', () {
      final samples = [
        sample(0, 50),
        sample(1, 52), // rise: no drain
        sample(2, 52, plug: PlugType.ac),
        sample(3, 55, plug: PlugType.ac),
        sample(4, 55),
        sample(60, 40), // gap: skipped
      ];
      final d = HistoryAnalysis.drain(samples);
      expect(d.overall.percent, 0);
      expect(d.overall.measured, const Duration(minutes: 1));
    });

    test('ranks the worst hours by rate', () {
      // 09:00-10:00 loses 1 %/h-ish, 10:00-11:00 loses 12 %/h, 11:00-12:00 loses 6 %/h.
      final samples = <HistorySample>[];
      var level = 90.0;
      for (var m = 0; m <= 180; m += 5) {
        final hour = m ~/ 60;
        final rate = [1.0, 12.0, 6.0, 0.0][hour];
        samples.add(sample(m, level.round()));
        level -= rate / 12;
      }
      final d = HistoryAnalysis.drain(samples);
      expect(d.worstHours.first, 10);
      expect(d.worstHours[1], 11);
      expect(d.byHour[10].perHour! > d.byHour[9].perHour!, isTrue);
    });

    test('an hour with under ten measured minutes is not ranked', () {
      final samples = [sample(0, 80), sample(5, 70)]; // 120 %/h but only 5 minutes
      final d = HistoryAnalysis.drain(samples);
      expect(d.worstHours, isEmpty);
    });

    test('no samples means no data', () {
      expect(HistoryAnalysis.drain(const []).hasData, isFalse);
      expect(HistoryAnalysis.drain([sample(0, 50)]).hasData, isFalse);
    });
  });

  group('series and ranges', () {
    test('levelSeries keeps small inputs and averages big ones into buckets', () {
      final small = [sample(0, 10), sample(1, 20)];
      expect(HistoryAnalysis.levelSeries(small), [10, 20]);

      final big = [for (var i = 0; i < 200; i++) sample(i, i < 100 ? 40 : 60)];
      final series = HistoryAnalysis.levelSeries(big, maxPoints: 10);
      expect(series, hasLength(10));
      expect(series.first, 40);
      expect(series.last, 60);
    });

    test('within keeps only recent samples', () {
      final samples = [sample(0, 50), sample(60 * 25, 40)];
      final now = _t0.add(const Duration(minutes: 60 * 25));
      final recent = HistoryAnalysis.within(samples, const Duration(hours: 24), now: now);
      expect(recent, hasLength(1));
      expect(recent.single.level, 40);
    });
  });

  test('HistorySample parses a channel map', () {
    final s = HistorySample.fromMap({
      'ts': _t0.millisecondsSinceEpoch,
      'level': 150,
      'currentMa': 1200,
      'voltageMv': 4000,
      'tempC': 31.5,
      'plugged': 'wireless',
      'screenOn': false,
    });
    expect(s.level, 100);
    expect(s.currentMa, 1200.0);
    expect(s.plug, PlugType.wireless);
    expect(s.screenOn, isFalse);
    expect(s.watts, closeTo(4.8, 1e-9));
  });
}
