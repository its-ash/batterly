import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import '../battery/battery_math.dart';
import '../battery/battery_platform.dart';
import '../battery/history_analysis.dart';

enum _Range {
  day('24 h', Duration(hours: 24)),
  week('7 days', Duration(days: 7));

  const _Range(this.label, this.span);
  final String label;
  final Duration span;
}

/// Level chart, drain per hour, screen on/off drain and charging sessions from the logged samples.
class HistoryScreen extends StatefulWidget {
  const HistoryScreen({super.key, required this.platform, required this.monitorOn, required this.onOpenSettings});

  final BatteryPlatform platform;

  /// Whether the background monitor is switched on, to word the empty state.
  final bool monitorOn;
  final VoidCallback onOpenSettings;

  @override
  State<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends State<HistoryScreen> {
  _Range _range = _Range.day;
  List<HistorySample>? _samples;
  Object? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final all = await widget.platform.samples(DateTime.now().subtract(const Duration(days: 7)));
      if (!mounted) return;
      setState(() {
        _samples = all;
        _error = null;
      });
    } catch (e) {
      if (mounted) setState(() => _error = e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final all = _samples;
    if (_error != null) return UiErrorState(message: 'Could not read the history.', onRetry: _load);
    if (all == null) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: UiSpace.xxxl),
        child: Center(child: UiSpinner(size: 28)),
      );
    }
    if (all.length < 2) {
      return UiEmptyState(
        icon: Icons.show_chart_rounded,
        title: 'No history yet',
        message: widget.monitorOn
            ? 'The background monitor is on. Give it a few minutes to record your battery.'
            : 'Turn on the background monitor in Settings. It records your battery once a minute for 7 days.',
        action: widget.monitorOn ? null : UiButton('Open Settings', icon: Icons.settings_rounded, onPressed: widget.onOpenSettings),
      );
    }

    final samples = HistoryAnalysis.within(all, _range.span);
    final drain = HistoryAnalysis.drain(samples);
    final sessions = HistoryAnalysis.sessions(samples).reversed.toList();
    return UiColumn(
      gap: UiSpace.lg,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        UiSegmentedControl<_Range>(
          [for (final r in _Range.values) UiSegment(r, label: r.label)],
          value: _range,
          expand: true,
          onChanged: (r) => setState(() => _range = r),
        ),
        _LevelCard(samples: samples, range: _range),
        _DrainCard(drain: drain),
        _ScreenCard(drain: drain),
        _SessionsCard(sessions: sessions),
        UiButton('Refresh', icon: Icons.refresh_rounded, variant: UiButtonVariant.secondary, onPressed: _load),
      ],
    );
  }
}

String _hourLabel(int h) => '${h.toString().padLeft(2, '0')}:00';

String _rate(double? perHour) => perHour == null ? '--' : '${perHour.toStringAsFixed(1)} %/h';

class _LevelCard extends StatelessWidget {
  const _LevelCard({required this.samples, required this.range});

  final List<HistorySample> samples;
  final _Range range;

  @override
  Widget build(BuildContext context) {
    final values = HistoryAnalysis.levelSeries(samples);
    return UiCard(
      child: UiColumn(
        gap: UiSpace.md,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          UiSectionHeader('Battery level', subtitle: 'Last ${range.label}'),
          if (values.length < 2)
            const Text('Not enough data in this range yet.')
          else
            UiLineChart(
              series: [UiChartSeries('Level', values)],
              height: 160,
              showLegend: false,
              yFormat: (v) => '${v.round()}%',
            ),
        ],
      ),
    );
  }
}

class _DrainCard extends StatelessWidget {
  const _DrainCard({required this.drain});

  final DrainAnalysis drain;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    if (!drain.hasData) {
      return const UiCard(
        child: UiEmptyState(
          compact: true,
          icon: Icons.battery_alert_rounded,
          title: 'No drain data',
          message: 'Drain is measured while unplugged. Use your phone on battery for a while.',
        ),
      );
    }
    final worst = drain.worstHours.toSet();
    return UiCard(
      child: UiColumn(
        gap: UiSpace.md,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          UiSectionHeader('Drain per hour', subtitle: 'Average ${_rate(drain.overall.perHour)} on battery'),
          UiBarChart(
            bars: [
              for (var h = 0; h < 24; h++)
                UiBarData(
                  h % 6 == 0 ? h.toString().padLeft(2, '0') : '',
                  drain.byHour[h].perHour ?? 0,
                  color: worst.contains(h) ? colors.danger : colors.accent,
                ),
            ],
            height: 150,
            yFormat: (v) => v.toStringAsFixed(v < 10 ? 1 : 0),
          ),
          if (drain.worstHours.isNotEmpty)
            UiChartLegend([
              UiLegendItem('Worst hours', color: colors.danger),
              UiLegendItem('Other', color: colors.accent),
            ]),
          for (final h in drain.worstHours)
            UiKeyValue('${_hourLabel(h)} to ${_hourLabel((h + 1) % 24)}', _rate(drain.byHour[h].perHour)),
        ],
      ),
    );
  }
}

class _ScreenCard extends StatelessWidget {
  const _ScreenCard({required this.drain});

  final DrainAnalysis drain;

  @override
  Widget build(BuildContext context) {
    if (!drain.hasData) return const SizedBox.shrink();
    final on = drain.screenOn, off = drain.screenOff;
    return UiCard(
      child: UiColumn(
        gap: UiSpace.sm,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const UiSectionHeader('Screen on vs off'),
          UiKeyValue('Screen on', '${_rate(on.perHour)} over ${BatteryMath.duration(on.measured)}'),
          UiKeyValue('Screen off', '${_rate(off.perHour)} over ${BatteryMath.duration(off.measured)}'),
        ],
      ),
    );
  }
}

class _SessionsCard extends StatelessWidget {
  const _SessionsCard({required this.sessions});

  final List<ChargeSession> sessions;

  @override
  Widget build(BuildContext context) {
    return UiCard(
      padding: const EdgeInsets.symmetric(vertical: UiSpace.md),
      child: UiColumn(
        gap: UiSpace.xs,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: UiSpace.lg),
            child: UiSectionHeader('Charging sessions'),
          ),
          if (sessions.isEmpty)
            const Padding(
              padding: EdgeInsets.all(UiSpace.lg),
              child: Text('No charging in this range.'),
            )
          else
            for (final s in sessions.take(20)) _SessionTile(session: s),
        ],
      ),
    );
  }
}

class _SessionTile extends StatelessWidget {
  const _SessionTile({required this.session});

  final ChargeSession session;

  @override
  Widget build(BuildContext context) {
    final s = session;
    final parts = <String>[
      BatteryMath.duration(s.duration),
      if (s.averageWatts != null) 'avg ${s.averageWatts!.toStringAsFixed(1)} W',
      if (s.peakWatts != null) 'peak ${s.peakWatts!.toStringAsFixed(1)} W',
    ];
    return UiListTile(
      '${s.startLevel}% to ${s.endLevel}%',
      subtitle: parts.join(' · '),
      icon: Icons.bolt_rounded,
      iconStatus: UiStatus.success,
      value: UiFormat.relativeTime(s.start),
      maxSubtitleLines: 2,
    );
  }
}
