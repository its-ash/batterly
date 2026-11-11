import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import 'battery/battery_platform.dart';
import 'screens/home_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(BatterlyApp(platform: MethodChannelBatteryPlatform()));
}

class BatterlyApp extends StatelessWidget {
  const BatterlyApp({super.key, required this.platform});

  final BatteryPlatform platform;

  @override
  Widget build(BuildContext context) {
    return UiApp(
      title: 'Batterly',
      home: HomeScreen(platform: platform),
    );
  }
}
