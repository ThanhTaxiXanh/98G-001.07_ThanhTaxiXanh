import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:thanhtaxixanh_driver/app/app.dart';
import 'package:thanhtaxixanh_driver/features/car_layout/layout_config.dart';
import 'package:thanhtaxixanh_driver/features/car_layout/layout_store.dart';
import 'package:thanhtaxixanh_driver/features/car_preview/car_preview.dart';
import 'package:thanhtaxixanh_driver/features/car_preview/live_map_tile.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('live map starts disabled and can be toggled without Android',
      (tester) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    SharedPreferences.setMockInitialValues({});
    final store = LayoutStore();
    await tester.pumpWidget(MaterialApp(home: SettingsHome(store: store)));
    expect(find.byType(LiveMapTile), findsNothing);
    await tester.tap(find.byKey(const Key('live-map-toggle')));
    await tester.pump();
    expect(find.byType(LiveMapTile), findsOneWidget);
    expect(find.text('Bản đồ động chỉ hỗ trợ điện thoại Android'),
        findsOneWidget);
    await tester.tap(find.byKey(const Key('live-map-toggle')));
    await tester.pump();
    expect(find.byType(LiveMapTile), findsNothing);
    store.dispose();
  });

  testWidgets('native map opt-in is bounded by 12x12 preview at varied ratios',
      (tester) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    for (final size in [(934, 844), (1920, 720), (800, 1280)]) {
      await tester.pumpWidget(MaterialApp(
        home: Scaffold(body: SingleChildScrollView(
          child: CarPreview(
            liveMap: true,
            config: CarLayoutConfig(
              targetWidthPx: size.$1,
              targetHeightPx: size.$2,
            ),
          ),
        )),
      ));
      await tester.pump();
      expect(find.byType(LiveMapTile), findsOneWidget);
      expect(tester.takeException(), isNull);
    }
  });
}
