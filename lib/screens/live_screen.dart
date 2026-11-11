import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import '../battery/battery_math.dart';
import '../battery/battery_snapshot.dart';
import '../battery/live_store.dart';

enum _ChartMode { watts, current }

/// Big charging current, the other live numbers and a two-minute chart.
class LiveScreen extends StatefulWidget {
  const LiveScreen({super.key, required this.store, required this.fahrenheit});

  final LiveStore store;
  final bool fahrenheit;

  @override
  State<LiveScreen> createState() => _LiveScreenState();
}

class _LiveScreenState extends State<LiveScreen> {
  _ChartMode _mode = _ChartMode.watts;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.store,
      builder: (context, _) {
        final s = widget.store.latest;
        if (s == null) {
          if (widget.store.error != null) {
            return UiErrorState(message: 'Could not read the battery.', onRetry: widget.store.start);
          }
          return const Padding(
            padding: EdgeInsets.symmetric(vertical: UiSpace.xxxl),
            child: Center(child: UiSpinner(size: 28)),
          );
        }
        return UiColumn(
          gap: UiSpace.lg,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _CurrentCard(snapshot: s),
            if (s.currentMa == null)
              const UiAlert(
                'This phone does not report the charging current, so only level, voltage and temperature are shown.',
                title: 'No current sensor',
                status: UiStatus.warning,
              ),
            _Stats(snapshot: s, store: widget.store, fahrenheit: widget.fahrenheit),
            if (s.currentMa != null) _ChartCard(store: widget.store, mode: _mode, onMode: (m) => setState(() => _mode = m)),
          ],
        );
      },
    );
  }
}

UiStatus _statusTone(BatterySnapshot s) => switch (s.status) {
  ChargeStatus.charging => UiStatus.success,
  ChargeStatus.discharging => UiStatus.neutral,
  ChargeStatus.full => UiStatus.info,
  _ => UiStatus.neutral,
};

class _CurrentCard extends StatelessWidget {
  const _CurrentCard({required this.snapshot});

  final BatterySnapshot snapshot;

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final ma = snapshot.currentMa;
    final watts = snapshot.watts;
    final color = switch ((ma ?? 0).sign) {
      > 0 => colors.success,
      < 0 => colors.warning,
      _ => colors.text,
    };
    return UiCard(
      variant: UiCardVariant.filled,
      padding: const EdgeInsets.all(UiSpace.xl),
      child: UiColumn(
        gap: UiSpace.sm,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          UiStatusPill(snapshot.status.label, status: _statusTone(snapshot), pulse: snapshot.isCharging),
          Text(
            ma == null ? '--' : BatteryMath.milliAmps(ma),
            style: context.text.displayLarge?.copyWith(color: color, fontSize: 64, height: 1.1),
            semanticsLabel: ma == null ? 'No current' : '${ma.round()} milliamperes',
          ),
          Text('mA', style: context.text.titleMedium?.copyWith(color: colors.textMuted)),
          if (watts != null)
            Text('${BatteryMath.wattsText(watts)} W', style: context.text.headlineMedium?.copyWith(color: color)),
        ],
      ),
    );
  }
}

class _Stats extends StatelessWidget {
  const _Stats({required this.snapshot, required this.store, required this.fahrenheit});

  final BatterySnapshot snapshot;
  final LiveStore store;
  final bool fahrenheit;

  @override
  Widget build(BuildContext context) {
    final s = snapshot;
    final remaining = store.timeRemaining;
    final hot = s.health == BatteryHealth.overheat || s.temperatureC >= 45;
    String? remainingLabel;
    if (s.isCharging) {
      remainingLabel = 'Time to full';
    } else if (s.status == ChargeStatus.discharging) {
      remainingLabel = 'Time left';
    }
    return UiResponsiveGrid(
      minItemWidth: 150,
      spacing: UiSpace.sm,
      runSpacing: UiSpace.sm,
      equalHeight: true,
      children: [
        UiStatCard(label: 'Battery', value: '${s.level}%', icon: Icons.battery_full_rounded),
        UiStatCard(
          label: 'Voltage',
          value: s.voltageMv > 0 ? '${s.volts.toStringAsFixed(2)} V' : '--',
          icon: Icons.electric_bolt_rounded,
        ),
        UiStatCard(
          label: 'Temperature',
          value: BatteryMath.temperature(s.temperatureC, fahrenheit: fahrenheit),
          icon: Icons.thermostat_rounded,
          iconStatus: hot ? UiStatus.danger : UiStatus.accent,
        ),
        UiStatCard(label: 'Charger', value: s.plug.label, icon: Icons.power_rounded),
        UiStatCard(
          label: 'Health',
          value: s.health.label,
          icon: Icons.favorite_rounded,
          iconStatus: switch (s.health) {
            BatteryHealth.good => UiStatus.success,
            BatteryHealth.unknown => UiStatus.neutral,
            _ => UiStatus.danger,
          },
        ),
        if (remainingLabel != null)
          UiStatCard(
            label: remainingLabel,
            value: remaining == null ? 'Calculating...' : BatteryMath.duration(remaining),
            icon: Icons.schedule_rounded,
          ),
        if (s.cycleCount != null) UiStatCard(label: 'Cycles', value: '${s.cycleCount}', icon: Icons.autorenew_rounded),
      ],
    );
  }
}

class _ChartCard extends StatelessWidget {
  const _ChartCard({required this.store, required this.mode, required this.onMode});

  final LiveStore store;
  final _ChartMode mode;
  final ValueChanged<_ChartMode> onMode;

  @override
  Widget build(BuildContext context) {
    final watts = mode == _ChartMode.watts;
    final values = watts ? store.wattsSeries : store.currentSeries;
    return UiCard(
      child: UiColumn(
        gap: UiSpace.md,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          UiSectionHeader('Last 2 minutes', subtitle: watts ? 'Power in watts' : 'Current in milliamperes'),
          UiSegmentedControl<_ChartMode>(
            const [UiSegment(_ChartMode.watts, label: 'Watts'), UiSegment(_ChartMode.current, label: 'mA')],
            value: mode,
            expand: true,
            onChanged: onMode,
          ),
          if (values.length < 2)
            const SizedBox(height: 160, child: Center(child: UiSpinner()))
          else
            UiLineChart(
              series: [UiChartSeries(watts ? 'Watts' : 'mA', values)],
              height: 160,
              showLegend: false,
              yFormat: (v) => watts ? v.toStringAsFixed(1) : v.round().toString(),
            ),
        ],
      ),
    );
  }
}
