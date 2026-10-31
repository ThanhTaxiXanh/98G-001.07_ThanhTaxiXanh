import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:thanhtaxixanh_driver/features/car_layout/grid12_layout.dart';
import 'package:thanhtaxixanh_driver/features/car_layout/layout_config.dart';
import 'package:thanhtaxixanh_driver/features/car_layout/layout_store.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('v1 slider ratios migrate into safe custom v2 layout', () {
    const legacy = '{"schemaVersion":1,"targetWidthPx":934,'
        '"targetHeightPx":844,"columnRatio":0.65,'
        '"calendarWeight":0.25,"qrWeight":0.45,"mediaWeight":0.30}';
    final migrated = CarLayoutConfig.decode(legacy);
    expect(migrated.schemaVersion, 2);
    expect(migrated.useCustomLayout, isTrue);
    expect(migrated.activeLayout.tile(GridPanel.map).width, 7);
    expect(migrated.activeLayout.tile(GridPanel.icons).width, 1);
    final saved = CarLayoutConfig.decode(migrated.encode());
    expect(saved.useCustomLayout, isTrue);
    expect(saved.activeLayout.tile(GridPanel.map).width, 7);
    expect(saved.columnRatio, 0.65);
  });

  test('v2 custom layout round-trips and invalid data fails closed', () {
    final config = const CarLayoutConfig().copyWith(
      customLayout: Grid12Presets.byId('wide_map').layout,
      useCustomLayout: true,
    );
    final restored = CarLayoutConfig.decode(config.encode());
    expect(restored.activeLayout.tile(GridPanel.map).width, 8);
    expect(restored.layoutPresetId, 'vf_e34');
    expect(() => CarLayoutConfig.decode(
      '{"schemaVersion":2,"layoutPresetId":"unknown"}'),
      throwsFormatException);
    expect(() => CarLayoutConfig.decode(
      '{"schemaVersion":2,"useCustomLayout":true}'),
      throwsFormatException);
    expect(() => CarLayoutConfig.decode(
      '{"schemaVersion":3}'),
      throwsFormatException);
  });

  test('saved custom layout survives another store load', () async {
    SharedPreferences.setMockInitialValues({});
    final store = LayoutStore();
    await store.load();
    final config = store.config.copyWith(
      useCustomLayout: true,
      customLayout: Grid12Presets.byId('bottom_qr').layout,
    );
    await store.update(config);
    final again = LayoutStore();
    await again.load();
    expect(again.config.useCustomLayout, isTrue);
    expect(again.config.activeLayout.tile(GridPanel.qr).y, 8);
    await again.update(again.config.copyWith(
      layoutPresetId: 'vf_e34', useCustomLayout: false));
    expect(again.config.activeLayout.tile(GridPanel.qr).height, 8);
    expect(again.config.customLayout, isNotNull);
    await again.update(again.config.copyWith(useCustomLayout: true));
    expect(again.config.activeLayout.tile(GridPanel.qr).y, 8);
    store.dispose();
    again.dispose();
  });
}
