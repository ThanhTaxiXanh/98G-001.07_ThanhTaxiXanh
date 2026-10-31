import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';
import '../car_layout/layout_config.dart';

/// This is a bounded, labeled DESIGN preview, not an Android Auto surface.
class CarPreview extends StatelessWidget {
  const CarPreview({super.key, required this.config, this.vietQrPayload});
  final CarLayoutConfig config;
  final String? vietQrPayload;

  @override
  Widget build(BuildContext context) {
    final rows = config.normalizedRows;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text('BẢN XEM TRƯỚC THIẾT KẾ • KHÔNG PHẢI MÀN HÌNH XE',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 10, color: Colors.orange)),
        const SizedBox(height: 6),
        AspectRatio(
          aspectRatio: config.targetWidthPx / config.targetHeightPx,
          child: LayoutBuilder(builder: (context, constraints) {
            // All content shrinks within the preview canvas.
            return ClipRRect(
              borderRadius: BorderRadius.circular(10),
              child: FittedBox(
                fit: BoxFit.fill,
                child: SizedBox(
                  width: config.targetWidthPx.toDouble(),
                  height: config.targetHeightPx.toDouble(),
                  child: ColoredBox(
                    color: const Color(0xff071423),
                    child: Column(children: [
                      SizedBox(
                        height: 72,
                        child: ColoredBox(
                          color: const Color(0xff11253b),
                          child: Row(children: const [
                            SizedBox(width: 22),
                            Icon(Icons.directions_car, color: Colors.amber, size: 32),
                            SizedBox(width: 16),
                            Text('THANHTAXIXANH',
                                style: TextStyle(color: Colors.amber, fontSize: 27,
                                    fontWeight: FontWeight.bold)),
                            Spacer(),
                            Text('BẢN THIẾT KẾ 934 × 844',
                                style: TextStyle(color: Colors.white70, fontSize: 15)),
                            SizedBox(width: 20),
                          ]),
                        ),
                      ),
                      Expanded(
                        child: Row(children: [
                          Expanded(
                            flex: (config.columnRatio * 100).round(),
                            child: Container(
                              margin: const EdgeInsets.all(9),
                              decoration: BoxDecoration(
                                borderRadius: BorderRadius.circular(12),
                                color: const Color(0xff102c3e),
                              ),
                              child: Stack(children: [
                                const Center(child: Column(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Icon(Icons.map_outlined, size: 96,
                                        color: Color(0xff527d91)),
                                    Text('VÙNG BẢN ĐỒ ANDROID AUTO',
                                        style: TextStyle(color: Colors.white, fontSize: 20)),
                                    Text('Chưa có bản đồ thực • OSM/MapLibre giai đoạn tiếp theo',
                                        style: TextStyle(color: Colors.white54, fontSize: 14)),
                                  ],
                                )),
                                Positioned(left: 12, bottom: 12,
                                    child: Text('GPS -- km/h    GIỚI HẠN --',
                                        style: TextStyle(color: Colors.amber, fontSize: 19))),
                              ]),
                            ),
                          ),
                          Expanded(
                            flex: 100 - (config.columnRatio * 100).round(),
                            child: Padding(
                              padding: const EdgeInsets.fromLTRB(0, 9, 9, 9),
                              child: Column(children: [
                                Expanded(
                                  flex: (rows[0] * 1000).round(),
                                  child: _panel(
                                    Icons.calendar_month, 'LỊCH ÂM / DƯƠNG',
                                    'Sự kiện chưa cấu hình',
                                  ),
                                ),
                                const SizedBox(height: 8),
                                Expanded(
                                  flex: (rows[1] * 1000).round(),
                                  child: Container(
                                    width: double.infinity,
                                    decoration: BoxDecoration(
                                      color: Colors.white,
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                    child: Column(children: [
                                      const SizedBox(height: 7),
                                      const Text('VIETQR • THANHTAXIXANH',
                                          style: TextStyle(color: Colors.black87,
                                              fontWeight: FontWeight.bold, fontSize: 16)),
                                      Expanded(child: Center(child:
                                        vietQrPayload == null
                                          ? const Icon(Icons.qr_code_2,
                                              color: Colors.black26, size: 95)
                                          : Padding(
                                              padding: const EdgeInsets.all(8),
                                              child: QrImageView(
                                                data: vietQrPayload!,
                                                version: QrVersions.auto,
                                                errorCorrectionLevel: QrErrorCorrectLevel.M,
                                                backgroundColor: Colors.white,
                                                padding: const EdgeInsets.all(8),
                                              ),
                                            ),
                                      )),
                                      Text(vietQrPayload == null
                                              ? 'CHƯA THIẾT LẬP TÀI KHOẢN'
                                              : 'QR TẠO TỪ CẤU HÌNH',
                                          style: const TextStyle(fontSize: 13,
                                              color: Colors.black54)),
                                      const SizedBox(height: 6),
                                    ]),
                                  ),
                                ),
                                const SizedBox(height: 8),
                                Expanded(
                                  flex: (rows[2] * 1000).round(),
                                  child: _panel(Icons.music_note, 'MEDIA',
                                      'Chưa chọn nguồn âm thanh'),
                                ),
                              ]),
                            ),
                          ),
                        ]),
                      ),
                    ]),
                  ),
                ),
              ),
            );
          }),
        ),
        const SizedBox(height: 4),
        Text('Preview: ${config.targetWidthPx} × ${config.targetHeightPx} px • '
            'cỡ hiển thị không phải viewport Android Auto',
            style: Theme.of(context).textTheme.bodySmall,
            textAlign: TextAlign.center),
      ],
    );
  }

  Widget _panel(IconData icon, String title, String details) => Container(
    width: double.infinity,
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(
      color: const Color(0xff18304a),
      borderRadius: BorderRadius.circular(12),
    ),
    child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
      Icon(icon, color: Colors.amber, size: 26),
      const SizedBox(height: 5),
      Text(title, textAlign: TextAlign.center,
          style: const TextStyle(color: Colors.white, fontSize: 19,
              fontWeight: FontWeight.bold)),
      Text(details, textAlign: TextAlign.center,
          style: const TextStyle(color: Colors.white70, fontSize: 14)),
    ]),
  );
}
