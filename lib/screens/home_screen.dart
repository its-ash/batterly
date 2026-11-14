import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import '../battery/battery_platform.dart';
import '../battery/live_store.dart';
import '../battery/settings_store.dart';
import 'devices_screen.dart';
import 'health_screen.dart';
import 'history_screen.dart';
import 'live_screen.dart';
import 'settings_screen.dart';

/// Live, History, Health and Devices tabs. The live stream runs only while the Live tab is on screen.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.platform});

  final BatteryPlatform platform;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late final LiveStore _live = LiveStore(widget.platform);
  late final SettingsStore _settings = SettingsStore(widget.platform);
  late final AppLifecycleListener _lifecycle;
  int _tab = 0;
  bool _foreground = true;

  @override
  void initState() {
    super.initState();
    _settings.load();
    _sync();
    // Back from a system permission screen: re-check. Hidden: stop reading the battery.
    _lifecycle = AppLifecycleListener(
      onResume: () {
        _foreground = true;
        _settings.load();
        _sync();
      },
      onHide: () {
        _foreground = false;
        _sync();
      },
    );
  }

  @override
  void dispose() {
    _lifecycle.dispose();
    _live.dispose();
    _settings.dispose();
    super.dispose();
  }

  void _sync() {
    if (_foreground && _tab == 0) {
      _live.start();
    } else {
      _live.stop();
    }
  }

  Future<void> _openSettings() async {
    _foreground = false;
    _sync();
    await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => SettingsScreen(store: _settings)));
    _foreground = true;
    _sync();
    if (mounted) setState(() {}); // the History tab re-reads when the monitor was switched
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _settings,
      builder: (context, _) {
        final config = _settings.config;
        return UiScaffold(
          appBar: UiAppBar(
            title: 'Batterly',
            actions: [UiIconButton(Icons.settings_rounded, tooltip: 'Settings', onPressed: _openSettings)],
          ),
          scrollable: true,
          body: UiColumn(
            gap: UiSpace.lg,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              UiTabs(
                tabs: const [
                  UiTab('Live', icon: Icons.bolt_rounded),
                  UiTab('History', icon: Icons.show_chart_rounded),
                  UiTab('Health', icon: Icons.favorite_rounded),
                  UiTab('Devices', icon: Icons.headphones_rounded),
                ],
                index: _tab,
                variant: UiTabsVariant.segment,
                onChanged: (i) {
                  setState(() => _tab = i);
                  _sync();
                },
              ),
              switch (_tab) {
                0 => LiveScreen(store: _live, fahrenheit: config.fahrenheit),
                1 => HistoryScreen(
                  key: ValueKey(config.monitor),
                  platform: widget.platform,
                  monitorOn: config.monitor,
                  onOpenSettings: _openSettings,
                ),
                2 => HealthScreen(
                  key: ValueKey(config.monitor),
                  platform: widget.platform,
                  monitorOn: config.monitor,
                  sdk: _settings.status.sdk,
                  onOpenSettings: _openSettings,
                ),
                _ => DevicesScreen(settings: _settings, onOpenSettings: _openSettings),
              },
            ],
          ),
        );
      },
    );
  }
}
