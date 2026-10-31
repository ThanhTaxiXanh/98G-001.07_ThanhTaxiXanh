import 'package:flutter/material.dart' hide GridTile;
import 'grid12_layout.dart';

/// Phone-only layout editor. Yellow corner = resize; drag tile = move.
class Grid12Editor extends StatefulWidget {
  const Grid12Editor({super.key, required this.layout, required this.ratio,
    required this.onEdited, this.onRejected});
  final Grid12Layout layout;
  final double ratio;
  final ValueChanged<Grid12Layout> onEdited;
  final ValueChanged<String>? onRejected;
  @override
  State<Grid12Editor> createState() => _Grid12EditorState();
}

class _Grid12EditorState extends State<Grid12Editor> {
  GridPanel selected = GridPanel.map;
  Grid12Layout? preview, original;
  GridTile? activeTile;
  Offset? start;
  bool resize = false, rejected = false;
  double w = 1, h = 1;

  Grid12Layout get value => preview ?? widget.layout;

  void begin(DragStartDetails d) {
    final p = d.localPosition;
    final tile = value.tiles.reversed.where((t) =>
        Grid12Geometry.bounds(t, w, h).contains(p.dx, p.dy)).firstOrNull;
    if (tile == null) {
      return;
    }
    setState(() => selected = tile.panel);
    if (tile.panel == GridPanel.icons) {
      return;
    }
    final b = Grid12Geometry.bounds(tile, w, h);
    activeTile = tile;
    original = value;
    start = p;
    resize = p.dx > b.right - 20 && p.dy > b.bottom - 20;
    rejected = false;
  }

  void update(DragUpdateDetails d) {
    final tile = activeTile;
    final origin = original;
    final p = start;
    if (tile == null || origin == null || p == null) {
      return;
    }
    final dx = ((d.localPosition.dx - p.dx) * 12 / w).round();
    final dy = ((d.localPosition.dy - p.dy) * 12 / h).round();
    try {
      final next = resize
          ? origin.edit(tile.panel, tile.x, tile.y,
              tile.width + dx, tile.height + dy)
          : origin.edit(tile.panel, tile.x + dx, tile.y + dy,
              tile.width, tile.height);
      setState(() => preview = next);
      rejected = false;
    } on FormatException {
      rejected = true;
    }
  }

  void end() {
    if (activeTile == null) {
      return;
    }
    final next = preview;
    if (next != null) {
      widget.onEdited(next);
    } else if (rejected) {
      widget.onRejected?.call('Ô chồng lấn hoặc vượt lưới.');
    }
    setState(() {
      preview = null; original = null; activeTile = null; start = null;
      rejected = false;
    });
  }

  @override
  Widget build(BuildContext context) => AspectRatio(
    aspectRatio: widget.ratio,
    child: LayoutBuilder(builder: (context, box) {
      w = box.maxWidth;
      h = box.maxHeight;
      return GestureDetector(
        key: const Key('grid12-editor'),
        behavior: HitTestBehavior.opaque,
        onPanStart: begin, onPanUpdate: update,
        onPanEnd: (_) => end(), onPanCancel: end,
        onTapUp: (d) {
          for (final t in value.tiles.reversed) {
            if (Grid12Geometry.bounds(t, w, h)
                .contains(d.localPosition.dx, d.localPosition.dy)) {
              setState(() => selected = t.panel);
              break;
            }
          }
        },
        child: CustomPaint(
          painter: Grid12EditorPainter(value, selected),
          child: const SizedBox.expand(),
        ),
      );
    }),
  );
}

class Grid12EditorPainter extends CustomPainter {
  Grid12EditorPainter(this.layout, this.selected);
  final Grid12Layout layout;
  final GridPanel selected;
  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawColor(const Color(0xff071423), BlendMode.src);
    const labels = {
      GridPanel.icons: '≡', GridPanel.map: 'BẢN ĐỒ',
      GridPanel.calendar: 'LỊCH', GridPanel.qr: 'VIETQR',
      GridPanel.media: 'MEDIA',
    };
    const colors = {
      GridPanel.icons: Color(0xff3b5165),
      GridPanel.map: Color(0xff194867),
      GridPanel.calendar: Color(0xff374b70),
      GridPanel.qr: Color(0xfff1f1f1),
      GridPanel.media: Color(0xff3b4756),
    };
    for (final tile in layout.tiles) {
      final b = Grid12Geometry.bounds(tile, size.width, size.height);
      final rect = Rect.fromLTRB(b.left, b.top, b.right, b.bottom);
      final brush = Paint()..isAntiAlias = true;
      brush.color = colors[tile.panel]!;
      canvas.drawRRect(RRect.fromRectAndRadius(rect, const Radius.circular(5)), brush);
      brush.color = tile.panel == selected ? Colors.amber : Colors.white38;
      brush.style = PaintingStyle.stroke;
      brush.strokeWidth = tile.panel == selected ? 3 : 1;
      canvas.drawRRect(RRect.fromRectAndRadius(rect, const Radius.circular(5)), brush);
      final caption = TextPainter(
        text: TextSpan(text: labels[tile.panel],
          style: TextStyle(fontSize: (b.width / 6).clamp(8.0, 17.0),
            color: tile.panel == GridPanel.qr ? Colors.black87 : Colors.white)),
        maxLines: 1, ellipsis: '…', textDirection: TextDirection.ltr,
      )..layout(maxWidth: (b.width - 5).clamp(1.0, size.width));
      caption.paint(canvas, Offset(b.left + (b.width - caption.width) / 2,
          b.top + (b.height - caption.height) / 2));
      if (tile.panel == selected && tile.panel != GridPanel.icons) {
        brush.style = PaintingStyle.fill;
        brush.color = Colors.amber;
        canvas.drawCircle(Offset(b.right - 5, b.bottom - 5), 5, brush);
      }
    }
  }
  @override
  bool shouldRepaint(covariant Grid12EditorPainter old) =>
      old.layout != layout || old.selected != selected;
}
