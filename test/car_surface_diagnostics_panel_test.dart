import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:thanhtaxixanh_driver/features/car_preview/car_surface_diagnostics_panel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('vn.g98g00107.thanhtaxixanh.driver/config');

  testWidgets('phone shows host dimensions and safe area from native JSON',
      (tester) async {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    final data = jsonEncode(<String, dynamic>{
      'version': 1,
      'lastObservedAt': 1793450000000,
      'lastEvent': 'stableAreaChanged',
      'surfaceWidth': 934,
      'surfaceHeight': 844,
      'visible': {
        'left': 0, 'top': 15, 'right': 934, 'bottom': 820,
        'width': 934, 'height': 805,
      },
      'stable': {
        'left': 25, 'top': 24, 'right': 910, 'bottom': 808,
        'width': 885, 'height': 784,
      },
      'safe': {
        'left': 25, 'top': 24, 'right': 910, 'bottom': 808,
        'width': 885, 'height': 784,
      },
      'overlaySafe': true,
      'surfacesCreated': 1,
      'surfacesDestroyed': 0,
      'visibleChanges': 1,
      'stableChanges': 1,
      'gestureEvents': 2,
      'events': [
        {'event': 'surfaceAvailable'},
        {'event': 'stableAreaChanged'},
      ],
    });
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'getCarSurfaceDiagnostics') return data;
      if (call.method == 'clearCarSurfaceDiagnostics') {
        return jsonEncode({'version': 1, 'eventCount': 0, 'events': []});
      }
      return null;
    });
    addTearDown(() => messenger.setMockMethodCallHandler(channel, null));

    await tester.pumpWidget(const MaterialApp(
      home: Scaffold(body: SingleChildScrollView(
        child: CarSurfaceDiagnosticsPanel(),
      )),
    ));
    await tester.pump();
    expect(find.textContaining('934 × 844 px'), findsOneWidget);
    expect(find.textContaining('[25, 24] → [910, 808]'), findsNWidgets(2));
    expect(find.textContaining('HUD ở vùng an toàn: Có'), findsOneWidget);
    expect(find.byKey(const Key('copy-car-surface')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('empty diagnostics never invent a connected car or sizes',
      (tester) async {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(channel, (call) async =>
        jsonEncode({'version': 1, 'eventCount': 0,
          'lastObservedSurfaceActive': false, 'events': []}));
    addTearDown(() => messenger.setMockMethodCallHandler(channel, null));

    await tester.pumpWidget(const MaterialApp(
      home: Scaffold(body: SingleChildScrollView(
        child: CarSurfaceDiagnosticsPanel(),
      )),
    ));
    await tester.pump();
    expect(find.textContaining('Chưa ghi nhận'), findsWidgets);
    expect(find.textContaining('-- × -- px'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
