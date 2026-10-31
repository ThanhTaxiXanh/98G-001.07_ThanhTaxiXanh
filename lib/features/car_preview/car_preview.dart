import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';
import '../car_layout/layout_config.dart';
import '../car_layout/grid12_layout.dart';

/// Preview-only design canvas. Real Android Auto host geometry is independent.
class CarPreview extends StatelessWidget {
  const CarPreview({super.key, required this.config, this.vietQrPayload});
  final CarLayoutConfig config;
  final String? vietQrPayload;

  Widget tileWidget(GridPanel panel, double w, double h) {
    if (panel == GridPanel.icons) {
      return const ColoredBox(color: Color(0xff35465c),
        child: Column(mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [Icon(Icons.home, color: Colors.lightBlueAccent),
            Icon(Icons.map, color: Colors.white70),
            Icon(Icons.apps, color: Colors.white70)]));
    }
    if (panel == GridPanel.qr) {
      return ColoredBox(color: Colors.white,
        child: Column(children: [
          const Padding(padding: EdgeInsets.only(top: 6),
            child: Text('ThanhTaxiXanh · VietQR',
              maxLines: 1, style: TextStyle(color: Colors.black87,
                fontWeight: FontWeight.bold, fontSize: 17))),
          Expanded(child: Center(child: vietQrPayload == null
              ? const Icon(Icons.qr_code_2, size: 100, color: Colors.black54)
              : AspectRatio(aspectRatio: 1, child: QrImageView(
                  data: vietQrPayload!, version: QrVersions.auto,
                  errorCorrectionLevel: QrErrorCorrectLevel.M,
                  backgroundColor: Colors.white,
                  padding: const EdgeInsets.all(9))))),
          const Padding(padding: EdgeInsets.only(bottom: 7),
            child: Text('QR từ tài khoản trên điện thoại',
              style: TextStyle(color: Colors.black54, fontSize: 12))),
        ]));
    }
    final title = switch (panel) {
      GridPanel.map => 'BẢN ĐỒ',
      GridPanel.calendar => 'LỊCH ÂM / DƯƠNG',
      GridPanel.media => 'MEDIA',
      _ => '',
    };
    return ColoredBox(color: const Color(0xff19354c),
      child: Stack(children: [
        Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
          Icon(switch (panel) {
            GridPanel.map => Icons.navigation,
            GridPanel.calendar => Icons.calendar_month,
            GridPanel.media => Icons.music_note,
            _ => Icons.widgets,
          }, color: Colors.amber, size: (h / 5).clamp(20.0, 62.0)),
          Text(title, maxLines: 1, overflow: TextOverflow.ellipsis,
            style: const TextStyle(color: Colors.white,
              fontWeight: FontWeight.bold, fontSize: 18)),
        ])),
        if (panel == GridPanel.map)
          const Positioned(bottom: 12, left: 12,
            child: Text('GPS -- km/h  ·  Giới hạn --',
              style: TextStyle(color: Colors.amber, fontSize: 14))),
      ]));
  }

  @override
  Widget build(BuildContext context) {
    final w = config.targetWidthPx.toDouble();
    final h = config.targetHeightPx.toDouble();
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text('BẢN XEM TRƯỚC THIẾT KẾ • KHÔNG PHẢI MÀN HÌNH XE',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: 10, color: Colors.orange)),
        const SizedBox(height: 6),
        AspectRatio(aspectRatio: w / h,
          child: ClipRRect(borderRadius: BorderRadius.circular(10),
            child: FittedBox(fit: BoxFit.fill,
              child: SizedBox(width: w, height: h,
                child: ColoredBox(color: const Color(0xff071423),
                  child: Stack(children: [
                    for (final tile in config.activeLayout.tiles)
                      Builder(builder: (context) {
                        final b = Grid12Geometry.bounds(tile, w, h);
                        return Positioned(
                          left: b.left, top: b.top, width: b.width,
                          height: b.height,
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(10),
                            child: tileWidget(tile.panel, b.width, b.height),
                          ),
                        );
                      }),
                  ])))))),
        const SizedBox(height: 4),
        Text('Preview: ' + config.targetWidthPx.toString() + ' × ' +
            config.targetHeightPx.toString() + ' px · lưới 12×12',
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.bodySmall),
      ]);
  }
}
