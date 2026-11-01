import 'dart:collection';

/// Design preview only; the Android Auto host controls its own viewport.
enum GridPanel { icons, map, calendar, qr, media }

class GridTile {
  const GridTile(this.panel, this.x, this.y, this.width, this.height);
  final GridPanel panel;
  final int x, y, width, height;

  bool get isValid {
    if (x < 0 || y < 0 || width < 1 || height < 1 ||
        x + width > 12 || y + height > 12) {
      return false;
    }
    if (panel == GridPanel.icons) {
      return x == 0 && y == 0 && width == 1 && height == 12;
    }
    if (x < 1) {
      return false;
    }
    if (panel == GridPanel.map) {
      return width >= 4 && height >= 5;
    }
    return width >= 2 && height >= 2;
  }

  bool overlaps(GridTile o) => x < o.x + o.width &&
      x + width > o.x && y < o.y + o.height && y + height > o.y;

  Map<String, Object> toJson() => {
    'panel': panel.name, 'x': x, 'y': y, 'width': width, 'height': height,
  };

  factory GridTile.fromJson(Map<String, dynamic> data) {
    final panel = GridPanel.values.where((p) => p.name == data['panel'])
        .firstOrNull;
    if (panel == null) throw const FormatException('Unknown grid panel');
    int n(String key) {
      if (data[key] is! int) throw const FormatException('Invalid coordinate');
      return data[key] as int;
    }
    return GridTile(panel, n('x'), n('y'), n('width'), n('height'));
  }
}

class Grid12Layout {
  Grid12Layout(Iterable<GridTile> values)
      : tiles = UnmodifiableListView<GridTile>(values.toList()) {
    if (tiles.length != GridPanel.values.length ||
        tiles.map((t) => t.panel).toSet().length !=
            GridPanel.values.length ||
        tiles.any((t) => !t.isValid)) {
      throw const FormatException('Invalid or incomplete 12x12 grid');
    }
    for (var i = 0; i < tiles.length; i++) {
      for (var j = i + 1; j < tiles.length; j++) {
        if (tiles[i].overlaps(tiles[j])) {
          throw const FormatException('Overlapping grid tiles');
        }
      }
    }
  }

  final List<GridTile> tiles;
  GridTile tile(GridPanel p) => tiles.firstWhere((t) => t.panel == p);

  Grid12Layout edit(GridPanel p, int x, int y, int w, int h) {
    if (p == GridPanel.icons) {
      throw const FormatException('Icon rail is fixed');
    }
    return Grid12Layout([
      for (final t in tiles)
        if (t.panel == p) GridTile(p, x, y, w, h) else t,
    ]);
  }

  /// Move a shared boundary by one or more grid cells. Both adjacent panels
  /// resize together, so fully packed presets remain editable.
  Grid12Layout shiftBoundary(GridPanel panel, GridPanel neighbor,
      int delta, {required bool vertical}) {
    if (panel == GridPanel.icons || neighbor == GridPanel.icons ||
        panel == neighbor || delta == 0) {
      throw const FormatException('Invalid boundary selection');
    }
    final a = tile(panel);
    final b = tile(neighbor);
    late final GridTile na, nb;
    if (vertical) {
      if (a.x != b.x || a.width != b.width ||
          a.y + a.height != b.y) {
        throw const FormatException('Panels do not share a horizontal edge');
      }
      na = GridTile(panel, a.x, a.y, a.width, a.height + delta);
      nb = GridTile(neighbor, b.x, b.y + delta, b.width, b.height - delta);
    } else {
      if (a.y != b.y || a.height != b.height ||
          a.x + a.width != b.x) {
        throw const FormatException('Panels do not share a vertical edge');
      }
      na = GridTile(panel, a.x, a.y, a.width + delta, a.height);
      nb = GridTile(neighbor, b.x + delta, b.y, b.width - delta, b.height);
    }
    return Grid12Layout([
      for (final t in tiles)
        if (t.panel == panel) na
        else if (t.panel == neighbor) nb
        else t,
    ]);
  }

  List<Map<String, Object>> toJson() =>
      tiles.map((t) => t.toJson()).toList(growable: false);

  factory Grid12Layout.fromJson(Object? raw) {
    if (raw is! List) throw const FormatException('Invalid grid');
    return Grid12Layout([
      for (final item in raw)
        if (item is Map<String, dynamic>) GridTile.fromJson(item)
        else throw const FormatException('Invalid tile'),
    ]);
  }
}

class GridPreset {
  const GridPreset(this.id, this.title, this.layout);
  final String id, title;
  final Grid12Layout layout;
}

class Grid12Presets {
  static Grid12Layout _layout(List<GridTile> items) =>
      Grid12Layout([const GridTile(GridPanel.icons, 0, 0, 1, 12), ...items]);

  static final List<GridPreset> all = List.unmodifiable([
    GridPreset('vf_e34', '01 · VF e34 · QR lớn', _layout([
      const GridTile(GridPanel.map, 1, 0, 6, 12),
      const GridTile(GridPanel.calendar, 7, 0, 5, 2),
      const GridTile(GridPanel.qr, 7, 2, 5, 8),
      const GridTile(GridPanel.media, 7, 10, 5, 2),
    ])),
    GridPreset('stacked', '02 · Ba tầng cân đối', _layout([
      const GridTile(GridPanel.map, 1, 0, 6, 12),
      const GridTile(GridPanel.calendar, 7, 0, 5, 4),
      const GridTile(GridPanel.qr, 7, 4, 5, 4),
      const GridTile(GridPanel.media, 7, 8, 5, 4),
    ])),
    GridPreset('calendar_first', '03 · Ưu tiên lịch', _layout([
      const GridTile(GridPanel.map, 1, 0, 6, 12),
      const GridTile(GridPanel.calendar, 7, 0, 5, 6),
      const GridTile(GridPanel.qr, 7, 6, 5, 4),
      const GridTile(GridPanel.media, 7, 10, 5, 2),
    ])),
    GridPreset('qr_first', '04 · Ưu tiên VietQR', _layout([
      const GridTile(GridPanel.map, 1, 0, 6, 12),
      const GridTile(GridPanel.qr, 7, 0, 5, 7),
      const GridTile(GridPanel.calendar, 7, 7, 5, 3),
      const GridTile(GridPanel.media, 7, 10, 5, 2),
    ])),
    GridPreset('wide_map', '05 · Bản đồ rộng', _layout([
      const GridTile(GridPanel.map, 1, 0, 8, 12),
      const GridTile(GridPanel.calendar, 9, 0, 3, 4),
      const GridTile(GridPanel.qr, 9, 4, 3, 5),
      const GridTile(GridPanel.media, 9, 9, 3, 3),
    ])),
    GridPreset('wide_tools', '06 · Tiện ích rộng', _layout([
      const GridTile(GridPanel.map, 1, 0, 5, 12),
      const GridTile(GridPanel.calendar, 6, 0, 6, 3),
      const GridTile(GridPanel.qr, 6, 3, 6, 7),
      const GridTile(GridPanel.media, 6, 10, 6, 2),
    ])),
    GridPreset('bottom_media', '07 · Media phía dưới', _layout([
      const GridTile(GridPanel.map, 1, 0, 7, 9),
      const GridTile(GridPanel.calendar, 8, 0, 4, 4),
      const GridTile(GridPanel.qr, 8, 4, 4, 5),
      const GridTile(GridPanel.media, 1, 9, 11, 3),
    ])),
    GridPreset('bottom_qr', '08 · VietQR phía dưới', _layout([
      const GridTile(GridPanel.map, 1, 0, 7, 8),
      const GridTile(GridPanel.calendar, 8, 0, 4, 4),
      const GridTile(GridPanel.media, 8, 4, 4, 4),
      const GridTile(GridPanel.qr, 1, 8, 11, 4),
    ])),
  ]);

  static GridPreset byId(String id) => all.firstWhere(
    (p) => p.id == id, orElse: () => all.first,
  );

  static bool exists(String id) => all.any((p) => p.id == id);

  /// Migrate old column / row sliders into a nearby validated grid.
  static Grid12Layout fromLegacy(double columnRatio,
      double calendarWeight, double qrWeight, double mediaWeight) {
    if (!columnRatio.isFinite || !calendarWeight.isFinite ||
        !qrWeight.isFinite || !mediaWeight.isFinite) {
      return all.first.layout;
    }
    final sum = calendarWeight + qrWeight + mediaWeight;
    if (sum <= 0) return all.first.layout;
    final mapW = (11 * columnRatio).round().clamp(4, 8);
    final col = 1 + mapW;
    final rightW = 11 - mapW;
    final calH = (12 * calendarWeight / sum).round().clamp(2, 8);
    final qrH = (12 * qrWeight / sum).round().clamp(2, 10 - calH);
    return _layout([
      GridTile(GridPanel.map, 1, 0, mapW, 12),
      GridTile(GridPanel.calendar, col, 0, rightW, calH),
      GridTile(GridPanel.qr, col, calH, rightW, qrH),
      GridTile(GridPanel.media, col, calH + qrH, rightW, 12 - calH - qrH),
    ]);
  }
}

class GridBounds {
  const GridBounds(this.left, this.top, this.right, this.bottom);
  final double left, top, right, bottom;
  double get width => right - left;
  double get height => bottom - top;
  bool contains(double x, double y) =>
      x >= left && x <= right && y >= top && y <= bottom;
}

/// One calculation shared by preview, editing and pointer hit-testing.
class Grid12Geometry {
  static GridBounds bounds(GridTile t, double w, double h) {
    if (!w.isFinite || !h.isFinite || w <= 0 || h <= 0) {
      throw const FormatException('Invalid viewport');
    }
    final pad = (w < h ? w : h) * 0.013;
    final inset = pad * 0.42;
    final uw = w - 2 * pad, uh = h - 2 * pad;
    return GridBounds(
      pad + t.x * uw / 12 + inset,
      pad + t.y * uh / 12 + inset,
      pad + (t.x + t.width) * uw / 12 - inset,
      pad + (t.y + t.height) * uh / 12 - inset,
    );
  }
}
