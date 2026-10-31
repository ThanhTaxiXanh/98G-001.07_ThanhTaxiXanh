import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:thanhtaxixanh_driver/features/traffic_signs/traffic_point_panel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('vn.g98g00107.thanhtaxixanh.driver/config');

  testWidgets('shows unverified totals and appends via native picker',
      (tester) async {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    final calls = <String>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      if (call.method == 'getTrafficPointStatus') {
        return <String, dynamic>{
          'total': 2,
          'unverified': 2,
          'verified': 0,
          'byKind': <String, int>{'speed_limit': 1, 'camera_speed': 1},
        };
      }
      if (call.method == 'importTrafficPointGeoJson') {
        expect(call.arguments, {'append': true});
        return <String, dynamic>{
          'total': 3,
          'unverified': 3,
          'verified': 0,
          'byKind': <String, int>{'speed_limit': 2, 'camera_speed': 1},
          'lastAdded': 1,
          'lastSkipped': 0,
        };
      }
      return null;
    });
    addTearDown(() => messenger.setMockMethodCallHandler(channel, null));

    await tester.pumpWidget(const MaterialApp(
      home: Scaffold(
        body: SingleChildScrollView(child: TrafficPointPanel()),
      ),
    ));
    await tester.pumpAndSettle();
    expect(find.textContaining('Tổng số điểm: 2'), findsOneWidget);
    expect(find.textContaining('Chưa xác minh: 2'), findsOneWidget);
    expect(find.textContaining('UNVERIFIED'), findsWidgets);
    await tester.tap(find.byKey(const Key('traffic-point-import')));
    await tester.pumpAndSettle();
    expect(find.textContaining('Tổng số điểm: 3'), findsOneWidget);
    expect(find.textContaining('Lần nhập: +1 điểm'), findsOneWidget);
    expect(calls, contains('importTrafficPointGeoJson'));
    expect(tester.takeException(), isNull);
  });

  testWidgets('shows Android-only fallback without inventing point data',
      (tester) async {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(channel, (call) async {
      throw MissingPluginException();
    });
    addTearDown(() => messenger.setMockMethodCallHandler(channel, null));

    await tester.pumpWidget(const MaterialApp(
      home: Scaffold(
        body: SingleChildScrollView(child: TrafficPointPanel()),
      ),
    ));
    await tester.pumpAndSettle();
    expect(find.textContaining('chỉ chạy trên điện thoại Android'),
        findsOneWidget);
    expect(find.textContaining('Tổng số điểm: 0'), findsOneWidget);
  });
}
