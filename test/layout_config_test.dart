import 'package:flutter_test/flutter_test.dart';
import 'package:thanhtaxixanh_driver/features/car_layout/layout_config.dart';

void main() {
  test('default profile exact target dimensions', () {
    const c = CarLayoutConfig();
    expect(c.targetWidthPx, 934);
    expect(c.targetHeightPx, 844);
    expect(c.columnRatio, 0.60);
    expect(c.normalizedRows.reduce((a, b) => a + b), closeTo(1, 0.00001));
  });
  test('serialization and row weight normalization', () {
    final c = const CarLayoutConfig().copyWith(
      columnRatio: 0.65, qrWeight: 0.70,
    );
    final restored = CarLayoutConfig.decode(c.encode());
    expect(restored.columnRatio, 0.65);
    expect(restored.normalizedRows.reduce((a, b) => a + b),
        closeTo(1, 0.00001));
  });
  test('reject future schema version', () {
    expect(
      () => CarLayoutConfig.decode('{"schemaVersion":3}'),
      throwsFormatException,
    );
  });
}
