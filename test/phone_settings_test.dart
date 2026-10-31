import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:thanhtaxixanh_driver/app/app.dart';
import 'package:thanhtaxixanh_driver/features/car_layout/layout_store.dart';

void main() {
  testWidgets('phone launches Settings and labeled preview, not dashboard',
      (tester) async {
    final store = LayoutStore();
    await tester.pumpWidget(MaterialApp(home: SettingsHome(store: store)));
    expect(find.text('Bố cục màn hình Android Auto'), findsOneWidget);
    expect(find.textContaining('BẢN XEM TRƯỚC THIẾT KẾ'), findsOneWidget);
    expect(find.byType(Slider), findsNWidgets(4));
    store.dispose();
  });
}
