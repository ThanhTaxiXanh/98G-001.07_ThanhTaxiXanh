import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:thanhtaxixanh_driver/features/car_layout/grid12_layout.dart';
import 'package:thanhtaxixanh_driver/features/car_layout/grid12_editor.dart';
import 'package:thanhtaxixanh_driver/features/car_layout/layout_config.dart';
import 'package:thanhtaxixanh_driver/features/car_preview/car_preview.dart';

void main() {
  testWidgets('VF e34 grid can resize a panel without overlaps',
      (tester) async {
    final initial = Grid12Presets.byId('vf_e34').layout;
    Grid12Layout? saved;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(body: Center(child: SizedBox(width: 360,
        child: Grid12Editor(
          layout: initial, ratio: 1.0,
          onEdited: (newLayout) => saved = newLayout,
        )))),
    ));
    final box = tester.getSize(find.byKey(const Key('grid12-editor')));
    final start = tester.getTopLeft(find.byKey(const Key('grid12-editor')));
    final map = Grid12Geometry.bounds(
        initial.tile(GridPanel.map), box.width, box.height);
    await tester.dragFrom(start + Offset(map.right - 6, map.bottom - 6),
      const Offset(-36, 0));
    await tester.pump();
    expect(saved, isNotNull);
    expect(saved!.tile(GridPanel.map).width, 5);
    expect(saved!.tile(GridPanel.qr).width, 5);
  });

  testWidgets('grid preview works for multiple aspect ratios',
      (tester) async {
    for (final profile in [(934, 844), (1920, 720), (800, 1280)]) {
      await tester.pumpWidget(MaterialApp(
        home: Scaffold(body: SingleChildScrollView(child: Padding(
          padding: const EdgeInsets.all(12),
          child: CarPreview(config: const CarLayoutConfig().copyWith(
            targetWidthPx: profile.$1, targetHeightPx: profile.$2,
          )),
        ))),
      ));
      await tester.pump();
      expect(find.textContaining('BẢN XEM TRƯỚC THIẾT KẾ'), findsOneWidget);
      expect(tester.takeException(), isNull);
    }
  });
}
