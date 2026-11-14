import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import '../battery/battery_platform.dart';
import '../battery/health.dart';

/// Estimated capacity and health, charge cycles, and the Android 14+ battery facts.
class HealthScreen extends StatefulWidget {
  const HealthScreen({
    super.key,
    required this.platform,
    required this.monitorOn,
    required this.sdk,
    required this.onOpenSettings,
  });

  final BatteryPlatform platform;
  final bool monitorOn;

  /// Android API level, for the Android 14+ card.
  final int sdk;
  final VoidCallback onOpenSettings;

  @override
  State<HealthScreen> createState() => _HealthScreenState();
}

class _HealthScreenState extends State<HealthScreen> {
  HealthInfo? _info;
  Object? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final info = await widget.platform.health();
      if (!mounted) return;
      setState(() {
        _info = info;
        _error = null;
      });
    } catch (e) {
      if (mounted) setState(() => _error = e);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_error != null) return UiErrorState(message: 'Could not read the battery health.', onRetry: _load);
    final info = _info;
    if (info == null) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: UiSpace.xxxl),
        child: Center(child: UiSpinner(size: 28)),
      );
    }
    return UiColumn(
      gap: UiSpace.lg,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _CapacityCard(info: info, monitorOn: widget.monitorOn, onOpenSettings: widget.onOpenSettings),
        _CyclesCard(info: info, monitorOn: widget.monitorOn),
        _AdvancedCard(info: info, sdk: widget.sdk),
        UiButton('Refresh', icon: Icons.refresh_rounded, variant: UiButtonVariant.secondary, onPressed: _load),
      ],
    );
  }
}

const _months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

String _date(DateTime d) => '${d.day} ${_months[d.month - 1]} ${d.year}';

/// "4,612" for 4612.3.
String _mah(double v) {
  final s = v.round().toString();
  final out = StringBuffer();
  for (var i = 0; i < s.length; i++) {
    if (i > 0 && (s.length - i) % 3 == 0) out.write(',');
    out.write(s[i]);
  }
  return out.toString();
}

UiStatus _gradeStatus(HealthGrade g) => switch (g) {
  HealthGrade.excellent || HealthGrade.good => UiStatus.success,
  HealthGrade.fair => UiStatus.warning,
  HealthGrade.worn => UiStatus.danger,
};

class _CapacityCard extends StatelessWidget {
  const _CapacityCard({required this.info, required this.monitorOn, required this.onOpenSettings});

  final HealthInfo info;
  final bool monitorOn;
  final VoidCallback onOpenSettings;

  @override
  Widget build(BuildContext context) {
    final est = info.estimatedMah;
    final health = info.healthPercent;
    final design = info.designMah;
    return UiCard(
      child: UiColumn(
        gap: UiSpace.md,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const UiSectionHeader('Capacity', subtitle: 'How much charge your battery really holds'),
          if (est == null) ...[
            Text(
              monitorOn
                  ? 'Measuring. Batterly needs one charge or discharge of 20% or more in one go (for example '
                        'charging from 30% to 80%) to estimate the capacity. More measurements make it more precise.'
                  : 'Turn on the background monitor. It measures the capacity while you charge or use your phone.',
            ),
            if (!monitorOn) UiButton('Open Settings', icon: Icons.settings_rounded, onPressed: onOpenSettings),
          ] else ...[
            if (health != null)
              Center(
                child: UiGauge(
                  value: health / 100,
                  label: 'Battery health · ${HealthGrade.of(health).label}',
                  status: _gradeStatus(HealthGrade.of(health)),
                  size: 200,
                ),
              ),
            UiKeyValueList([
              UiKeyValue('Estimated capacity', '${_mah(est)} mAh'),
              UiKeyValue('Design capacity', design == null ? 'Not reported by this phone' : '${_mah(design)} mAh'),
              UiKeyValue(
                'Based on',
                info.estimates.length == 1
                    ? '1 measurement'
                    : '${info.estimates.length.clamp(1, HealthInfo.window)} latest of ${info.estimates.length} measurements',
              ),
            ]),
            if (info.estimates.length >= 3)
              UiLineChart(
                series: [
                  UiChartSeries('mAh', [for (final e in info.estimates) e.mah]),
                ],
                labels: [for (final e in info.estimates) '${e.time.day} ${_months[e.time.month - 1]}'],
                height: 140,
                showLegend: false,
                yFormat: (v) => v.round().toString(),
              ),
            Text(
              'Estimated from the charge counter between level changes. A single measurement can be off by a few '
              'percent; the result settles after several charges.',
              style: context.text.bodySmall?.copyWith(color: context.colors.textMuted),
            ),
          ],
        ],
      ),
    );
  }
}

class _CyclesCard extends StatelessWidget {
  const _CyclesCard({required this.info, required this.monitorOn});

  final HealthInfo info;
  final bool monitorOn;

  @override
  Widget build(BuildContext context) {
    final since = info.cycleSince;
    return UiCard(
      child: UiColumn(
        gap: UiSpace.md,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const UiSectionHeader('Charge cycles', subtitle: 'One cycle = 100% charged in total'),
          UiKeyValueList([
            UiKeyValue('Reported by Android', info.cycleCount?.toString() ?? 'Not reported (needs Android 14)'),
            if (since != null) ...[
              UiKeyValue('Counted by Batterly', info.estimatedCycles.toStringAsFixed(1)),
              UiKeyValue('Counting since', '${_date(since)} · ${info.chargedPercent}% charged'),
            ],
          ]),
          if (since == null)
            Text(
              monitorOn
                  ? 'Batterly starts counting at your next charge.'
                  : 'Turn on the background monitor and Batterly counts every percent you charge.',
              style: context.text.bodySmall?.copyWith(color: context.colors.textMuted),
            ),
        ],
      ),
    );
  }
}

class _AdvancedCard extends StatelessWidget {
  const _AdvancedCard({required this.info, required this.sdk});

  static const command = 'adb shell pm grant com.itsash.batterly android.permission.BATTERY_STATS';

  final HealthInfo info;
  final int sdk;

  @override
  Widget build(BuildContext context) {
    final muted = context.text.bodySmall?.copyWith(color: context.colors.textMuted);
    return UiCard(
      child: UiColumn(
        gap: UiSpace.md,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const UiSectionHeader('Official battery data', subtitle: 'State of health and battery dates'),
          if (sdk < 34)
            const UiAlert('Android reports these on Android 14 and later only.', status: UiStatus.neutral)
          else if (!info.statsGranted) ...[
            const Text(
              'Android 14 can report the official state of health, the manufacturing date and the first-use date, '
              'but only to apps given a special permission from a computer. Connect your phone with USB debugging '
              'on and run once:',
            ),
            UiKeyValue('Command', command, copyable: true, mono: true, stacked: true, maxLines: 4),
            Text('Then come back and tap Refresh. Not every phone fills in these values.', style: muted),
          ] else ...[
            UiKeyValueList([
              UiKeyValue('State of health', info.stateOfHealth == null ? 'Not reported' : '${info.stateOfHealth}%'),
              UiKeyValue('Manufactured', info.manufactureDate == null ? 'Not reported' : _date(info.manufactureDate!)),
              UiKeyValue('First used', info.firstUseDate == null ? 'Not reported' : _date(info.firstUseDate!)),
              UiKeyValue('Charging mode', info.chargingPolicyLabel ?? 'Not reported'),
            ]),
            Text(
              'Values come straight from the phone. "Not reported" means this phone leaves them empty.',
              style: muted,
            ),
          ],
        ],
      ),
    );
  }
}
