import 'package:flutter_test/flutter_test.dart';
import 'package:thanhtaxixanh/features/car_layout/grid12_layout.dart';

void main() {
  test('stacked panels can share a row boundary', () {
    final layout = Grid12Presets.byId('stacked').layout;
    final next = layout.shiftBoundary(
      GridPanel.calendar, GridPanel.qr, 1, vertical: true);
    expect(next.tile(GridPanel.calendar).height, 5);
    expect(next.tile(GridPanel.qr).y, 5);
    expect(next.tile(GridPanel.qr).height, 3);
  });

  test('map and calendar cannot resize if not full-height neighbors', () {
    final layout = Grid12Presets.byId('stacked').layout;
    expect(() => layout.shiftBoundary(
      GridPanel.map, GridPanel.calendar, 1, vertical: false),
      throwsFormatException);
  });

  test('invalid over-shrink rejects edit without changing original', () {
    final layout = Grid12Presets.byId('stacked').layout;
    expect(() => layout.shiftBoundary(
      GridPanel.calendar, GridPanel.qr, 3, vertical: true),
      throwsFormatException);
    expect(layout.tile(GridPanel.calendar).height, 4);
  });
}
