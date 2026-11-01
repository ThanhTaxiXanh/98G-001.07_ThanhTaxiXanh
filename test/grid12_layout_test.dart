import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:thanhtaxixanh_driver/features/car_layout/grid12_layout.dart';

void main() {
  test('8 complete nonoverlapping layouts', () {
    expect(Grid12Presets.all, hasLength(8));
    for (final preset in Grid12Presets.all) {
      final tiles = preset.layout.tiles;
      expect(tiles, hasLength(5));
      for (final a in tiles) {
        expect(a.isValid, isTrue);
        for (final b in tiles) {
          if (a.panel != b.panel) expect(a.overlaps(b), isFalse);
        }
      }
    }
  });
  test('VF e34 matches visual proportions', () {
    final l = Grid12Presets.byId('vf_e34').layout;
    expect(l.tile(GridPanel.map).width, 6);
    expect(l.tile(GridPanel.qr).height, 8);
    expect(l.tile(GridPanel.calendar).height, 2);
    expect(l.tile(GridPanel.media).height, 2);
  });
  test('geometries fit wide and portrait preview resolutions', () {
    for (final size in [(934, 844), (800, 480), (1920, 720), (800, 1280)]) {
      for (final preset in Grid12Presets.all) {
        for (final tile in preset.layout.tiles) {
          final r = Grid12Geometry.bounds(tile, size.$1.toDouble(), size.$2.toDouble());
          expect(r.left, greaterThanOrEqualTo(0));
          expect(r.top, greaterThanOrEqualTo(0));
          expect(r.right, lessThanOrEqualTo(size.$1));
          expect(r.bottom, lessThanOrEqualTo(size.$2));
          expect(r.width, greaterThan(0));
          expect(r.height, greaterThan(0));
        }
      }
    }
  });
  test('reject icon rail edits and overlaps without mutating original', () {
    final l = Grid12Presets.all.first.layout;
    expect(() => l.edit(GridPanel.icons, 1, 0, 1, 12), throwsFormatException);
    expect(() => l.edit(GridPanel.map, 7, 0, 6, 12), throwsFormatException);
    expect(() => l.edit(GridPanel.qr, 11, 2, 5, 8), throwsFormatException);
    expect(l.tile(GridPanel.map).x, 1);
  });
  test('JSON round-trip and malformed layouts', () {
    final l = Grid12Presets.all.last.layout;
    expect(Grid12Layout.fromJson(jsonDecode(jsonEncode(l.toJson())))
        .tile(GridPanel.qr).height, 4);
    expect(() => Grid12Layout.fromJson([]), throwsFormatException);
    expect(() => Grid12Layout.fromJson([l.tiles.first.toJson()]),
        throwsFormatException);
  });
  test('old slider ratios migrate into safe three-row layout', () {
    final l = Grid12Presets.fromLegacy(0.65, 0.25, 0.45, 0.30);
    expect(l.tile(GridPanel.map).width, 7);
    expect(l.tile(GridPanel.qr).y, l.tile(GridPanel.calendar).height);
    expect(l.tile(GridPanel.media).y + l.tile(GridPanel.media).height, 12);
  });
}
