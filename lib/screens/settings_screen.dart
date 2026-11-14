import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import '../battery/battery_platform.dart';
import '../battery/settings_store.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key, required this.store});

  final SettingsStore store;

  @override
  Widget build(BuildContext context) {
    return UiScaffold(
      appBar: const UiAppBar(title: 'Settings'),
      scrollable: true,
      body: ListenableBuilder(
        listenable: store,
        builder: (context, _) {
          if (!store.loaded) return const Center(child: UiSpinner());
          final c = store.config;
          final s = store.status;
          return UiColumn(
            gap: UiSpace.lg,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              UiListGroup(
                header: 'Background monitor',
                footer:
                    'Shows live power in a notification, records your battery every minute for 7 days '
                    'and runs the alarms below, even when the app is closed.',
                children: [
                  UiSwitchTile(
                    'Background monitor',
                    subtitle: c.monitor ? 'On. Recording your battery.' : 'Off',
                    icon: Icons.monitor_heart_rounded,
                    status: c.monitor ? UiStatus.success : UiStatus.neutral,
                    value: c.monitor,
                    onChanged: store.setMonitor,
                  ),
                  if (c.monitor)
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: UiSpace.lg, vertical: UiSpace.sm),
                      child: UiColumn(
                        gap: UiSpace.sm,
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          Text('Status bar icon', style: context.text.titleSmall),
                          UiSegmentedControl<StatusIconMode>(
                            [for (final m in StatusIconMode.values) UiSegment(m, label: m.label)],
                            value: c.statusIcon,
                            expand: true,
                            onChanged: store.setStatusIcon,
                          ),
                          Text(switch (c.statusIcon) {
                            StatusIconMode.icon => 'A battery icon in the status bar.',
                            StatusIconMode.level => 'The battery percent as the status bar icon.',
                            StatusIconMode.watts => 'Charging watts while charging, the percent otherwise.',
                          }, style: context.text.bodySmall?.copyWith(color: context.colors.textMuted)),
                        ],
                      ),
                    ),
                  if (!s.notifications)
                    UiListTile(
                      'Allow notifications',
                      subtitle: 'Needed to show the live notification and the alarms.',
                      icon: Icons.notifications_off_rounded,
                      iconStatus: UiStatus.warning,
                      chevron: true,
                      onTap: () => store.openPermission('notifications'),
                    ),
                  if (c.monitor && !s.batteryOptimizationsIgnored)
                    UiListTile(
                      'Keep running in the background',
                      subtitle: 'Exclude Batterly from battery optimization so Android does not stop it.',
                      icon: Icons.battery_saver_rounded,
                      iconStatus: UiStatus.warning,
                      chevron: true,
                      onTap: () => store.openPermission('battery'),
                    ),
                ],
              ),
              UiListGroup(
                header: 'Alarms',
                footer: 'Alarms work while the background monitor is on.',
                children: [
                  UiSwitchTile(
                    'Charge limit alarm',
                    subtitle: 'Notify when charging reaches ${c.chargeLimit}%.',
                    icon: Icons.battery_charging_full_rounded,
                    value: c.chargeAlarm,
                    onChanged: store.setChargeAlarm,
                  ),
                  if (c.chargeAlarm)
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: UiSpace.lg, vertical: UiSpace.sm),
                      child: UiSlider(
                        label: 'Charge limit',
                        value: c.chargeLimit.toDouble(),
                        min: 50,
                        max: 100,
                        divisions: 50,
                        format: (v) => '${v.round()}%',
                        onChanged: (v) => store.setChargeLimit(v.round()),
                      ),
                    ),
                  UiSwitchTile(
                    'Low battery reminder',
                    subtitle: 'Notify at ${c.lowLevel}% when not charging.',
                    icon: Icons.battery_alert_rounded,
                    value: c.lowReminder,
                    onChanged: store.setLowReminder,
                  ),
                  if (c.lowReminder)
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: UiSpace.lg, vertical: UiSpace.sm),
                      child: UiSlider(
                        label: 'Remind me at',
                        value: c.lowLevel.toDouble(),
                        min: 5,
                        max: 50,
                        divisions: 45,
                        format: (v) => '${v.round()}%',
                        onChanged: (v) => store.setLowLevel(v.round()),
                      ),
                    ),
                  UiSwitchTile(
                    'Vibrate on alarm',
                    icon: Icons.vibration_rounded,
                    value: c.vibrate,
                    onChanged: store.setVibrate,
                  ),
                ],
              ),
              UiSection(
                title: 'Temperature unit',
                child: UiSegmentedControl<bool>(
                  const [UiSegment(false, label: '°C'), UiSegment(true, label: '°F')],
                  value: c.fahrenheit,
                  expand: true,
                  onChanged: store.setFahrenheit,
                ),
              ),
              const UiAppearanceSection(),
              const UiUpdateSection(storeId: 'batterly'),
              const UiDeveloperSection(),
            ],
          );
        },
      ),
    );
  }
}
