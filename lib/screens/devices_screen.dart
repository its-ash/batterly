import 'dart:async';

import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import '../battery/health.dart';
import '../battery/settings_store.dart';

/// Battery levels of paired Bluetooth devices, refreshed every 10 seconds while visible.
class DevicesScreen extends StatefulWidget {
  const DevicesScreen({super.key, required this.settings, required this.onOpenSettings});

  final SettingsStore settings;
  final VoidCallback onOpenSettings;

  @override
  State<DevicesScreen> createState() => _DevicesScreenState();
}

class _DevicesScreenState extends State<DevicesScreen> {
  static const _interval = Duration(seconds: 10);

  List<BtDevice>? _devices;
  Object? _error;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _load();
    _timer = Timer.periodic(_interval, (_) => _load());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _load() async {
    if (!widget.settings.status.bluetooth) return;
    try {
      final list = await widget.settings.platform.bluetoothDevices();
      if (!mounted) return;
      setState(() {
        _devices = list;
        _error = null;
      });
    } catch (e) {
      if (mounted) setState(() => _error = e);
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.settings,
      builder: (context, _) {
        if (!widget.settings.status.bluetooth) {
          return UiEmptyState(
            icon: Icons.bluetooth_disabled_rounded,
            title: 'Allow Bluetooth',
            message:
                'Batterly needs the Nearby devices permission to read the battery of your earbuds, watch '
                'and other Bluetooth devices. It does not scan or connect to anything.',
            action: UiButton(
              'Allow',
              icon: Icons.bluetooth_rounded,
              onPressed: () => widget.settings.openPermission('bluetooth'),
            ),
          );
        }
        if (_error != null) return UiErrorState(message: 'Could not read Bluetooth devices.', onRetry: _load);
        final devices = _devices;
        if (devices == null) {
          // Permission just granted: load now instead of waiting for the timer.
          WidgetsBinding.instance.addPostFrameCallback((_) => _load());
          return const Padding(
            padding: EdgeInsets.symmetric(vertical: UiSpace.xxxl),
            child: Center(child: UiSpinner(size: 28)),
          );
        }
        final connected = devices.where((d) => d.connected).toList();
        final others = devices.where((d) => !d.connected).toList();
        final config = widget.settings.config;
        return UiColumn(
          gap: UiSpace.lg,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (devices.isEmpty)
              const UiEmptyState(
                icon: Icons.bluetooth_rounded,
                title: 'No paired devices',
                message: 'Turn on Bluetooth and pair your earbuds, headphones or watch to see their battery here.',
              )
            else ...[
              if (connected.isEmpty)
                const UiAlert(
                  'Paired devices show their battery here once they connect.',
                  title: 'Nothing connected',
                  status: UiStatus.neutral,
                )
              else
                UiListGroup(
                  header: 'Connected',
                  footer: 'Not every device reports its battery. Those show "No battery level".',
                  children: [for (final d in connected) _DeviceTile(device: d, lowLevel: config.btLowLevel)],
                ),
              if (others.isNotEmpty)
                UiListGroup(
                  header: 'Paired, not connected',
                  children: [for (final d in others) _DeviceTile(device: d, lowLevel: config.btLowLevel)],
                ),
            ],
            UiListGroup(
              header: 'Alert',
              footer: config.monitor
                  ? 'Works while the background monitor is on.'
                  : 'Needs the background monitor, which is off.',
              children: [
                UiSwitchTile(
                  'Low battery alert',
                  subtitle: 'Notify when a connected device drops to ${config.btLowLevel}%.',
                  icon: Icons.notifications_active_rounded,
                  value: config.btAlert,
                  onChanged: widget.settings.setBtAlert,
                ),
                if (config.btAlert)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: UiSpace.lg, vertical: UiSpace.sm),
                    child: UiSlider(
                      label: 'Alert at',
                      value: config.btLowLevel.toDouble(),
                      min: 5,
                      max: 50,
                      divisions: 45,
                      format: (v) => '${v.round()}%',
                      onChanged: (v) => widget.settings.setBtLowLevel(v.round()),
                    ),
                  ),
                if (!config.monitor)
                  UiListTile(
                    'Turn on the background monitor',
                    icon: Icons.monitor_heart_rounded,
                    iconStatus: UiStatus.warning,
                    chevron: true,
                    onTap: widget.onOpenSettings,
                  ),
              ],
            ),
          ],
        );
      },
    );
  }
}

IconData _icon(BtKind kind) => switch (kind) {
  BtKind.headphones => Icons.headphones_rounded,
  BtKind.watch => Icons.watch_rounded,
  BtKind.keyboard => Icons.keyboard_rounded,
  BtKind.mouse => Icons.mouse_rounded,
  BtKind.phone => Icons.smartphone_rounded,
  BtKind.computer => Icons.laptop_rounded,
  BtKind.car => Icons.directions_car_rounded,
  BtKind.other => Icons.bluetooth_rounded,
};

class _DeviceTile extends StatelessWidget {
  const _DeviceTile({required this.device, required this.lowLevel});

  final BtDevice device;
  final int lowLevel;

  @override
  Widget build(BuildContext context) {
    final level = device.level;
    final low = level != null && level <= lowLevel;
    return UiListTile(
      device.name,
      icon: _icon(device.kind),
      iconStatus: !device.connected ? UiStatus.neutral : (low ? UiStatus.danger : UiStatus.accent),
      subtitle: !device.connected ? 'Not connected' : (level == null ? 'No battery level' : null),
      trailing: level == null
          ? null
          : SizedBox(
              width: 110,
              child: UiProgressBar(
                level / 100,
                label: '$level%',
                status: low ? UiStatus.danger : (level <= 50 ? UiStatus.warning : UiStatus.success),
              ),
            ),
    );
  }
}
