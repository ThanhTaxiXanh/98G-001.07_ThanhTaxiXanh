import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Android Auto Surface diagnostics, collected on the phone without GPS tracks.
class CarSurfaceDiagnosticsPanel extends StatefulWidget {
  const CarSurfaceDiagnosticsPanel({super.key});

  @override
  State<CarSurfaceDiagnosticsPanel> createState() =>
      _CarSurfaceDiagnosticsPanelState();
}

class _CarSurfaceDiagnosticsPanelState
    extends State<CarSurfaceDiagnosticsPanel> {
  static const channel =
      MethodChannel('vn.g98g00107.thanhtaxixanh.driver/config');
  Map<String, dynamic> report = const {};
  String? error;
  bool busy = false;

  @override
  void initState() {
    super.initState();
    unawaited(_read());
  }

  Future<void> _read({bool clear = false}) async {
    if (busy) return;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final raw = await channel.invokeMethod<String>(
          clear ? 'clearCarSurfaceDiagnostics' : 'getCarSurfaceDiagnostics');
      final value = raw == null ? <String, dynamic>{}
          : jsonDecode(raw) as Map<String, dynamic>;
      if (mounted) setState(() => report = value);
    } on MissingPluginException {
      if (mounted) {
        setState(() => error = 'Báo cáo chỉ có trên điện thoại Android.');
      }
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() => error = e.message ?? 'Không đọc được báo cáo Surface');
      }
    } on FormatException {
      if (mounted) setState(() => error = 'Báo cáo Surface không hợp lệ');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  String _rect(Object? value) {
    if (value is! Map) return 'Chưa nhận';
    return '[${value['left']}, ${value['top']}] → '
        '[${value['right']}, ${value['bottom']}] '
        '(${value['width']} × ${value['height']} px)';
  }

  String _lastSeen() {
    final n = report['lastObservedAt'];
    if (n is! num || n <= 0) return 'Chưa ghi nhận';
    final d = DateTime.fromMillisecondsSinceEpoch(n.toInt()).toLocal();
    return '${d.day}/${d.month}/${d.year} '
        '${d.hour.toString().padLeft(2, '0')}:'
        '${d.minute.toString().padLeft(2, '0')}:'
        '${d.second.toString().padLeft(2, '0')}';
  }

  Widget _line(String title, String value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 4),
    child: Text('$title: $value'),
  );

  Future<void> _confirmClear() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Xóa nhật ký chẩn đoán?'),
        content: const Text('Chỉ xóa các sự kiện kỹ thuật Android Auto. '
            'Dữ liệu đường và bố cục không bị ảnh hưởng.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false),
              child: const Text('Hủy')),
          FilledButton(onPressed: () => Navigator.pop(context, true),
              child: const Text('Xóa nhật ký')),
        ],
      ),
    );
    if (confirmed == true && mounted) await _read(clear: true);
  }

  @override
  Widget build(BuildContext context) {
    final events = report['events'];
    final entries = events is List ? events : const [];
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      const Text('Chỉ ghi nhận lần kết nối gần nhất. Trạng thái được lưu '
          'không chứng minh xe đang kết nối. Không lưu hành trình, GPS hay QR.'),
      const SizedBox(height: 8),
      Wrap(spacing: 8, runSpacing: 8, children: [
        OutlinedButton.icon(
          key: const Key('refresh-car-surface'),
          onPressed: busy ? null : () => unawaited(_read()),
          icon: const Icon(Icons.refresh),
          label: const Text('Đọc trạng thái Surface'),
        ),
        OutlinedButton.icon(
          key: const Key('copy-car-surface'),
          onPressed: report.isEmpty ? null : () async {
            await Clipboard.setData(ClipboardData(
                text: const JsonEncoder.withIndent('  ').convert(report)));
            if (context.mounted) {
              ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
                content: Text('Đã sao chép báo cáo kỹ thuật'),
              ));
            }
          },
          icon: const Icon(Icons.copy),
          label: const Text('Sao chép báo cáo'),
        ),
        TextButton.icon(
          onPressed: busy ? null : () => unawaited(_confirmClear()),
          icon: const Icon(Icons.delete_outline),
          label: const Text('Xóa nhật ký'),
        ),
      ]),
      if (error != null)
        Text(error!, style: const TextStyle(color: Colors.orange)),
      _line('Ghi nhận gần nhất', _lastSeen()),
      _line('Sự kiện cuối', '${report['lastEvent'] ?? 'Chưa có'}'),
      _line('Surface', '${report['surfaceWidth'] ?? '--'} × '
          '${report['surfaceHeight'] ?? '--'} px'),
      _line('Mật độ host (DPI)', '${report['surfaceDpi'] ?? 'Chưa nhận'}'),
      _line('Tỷ lệ Surface', report['surfaceAspectRatio'] is num
          ? (report['surfaceAspectRatio'] as num).toStringAsFixed(3)
          : 'Chưa nhận'),
      _line('Visible Area', _rect(report['visible'])),
      _line('Stable Area', _rect(report['stable'])),
      _line('Safe Area', _rect(report['safe'])),
      _line('HUD ở vùng an toàn',
          report['overlaySafe'] == true ? 'Có' : 'Chưa xác nhận'),
      if (report['warning'] is String)
        Text(report['warning'] as String,
            style: const TextStyle(color: Colors.orange)),
      _line('Surface đã tạo / đã hủy',
          '${report['surfacesCreated'] ?? 0} / '
          '${report['surfacesDestroyed'] ?? 0}'),
      _line('Cập nhật visible / stable',
          '${report['visibleChanges'] ?? 0} / '
          '${report['stableChanges'] ?? 0}'),
      _line('Số cử chỉ host chuyển tiếp',
          '${report['gestureEvents'] ?? 0}'),
      _line('Số lượt vẽ đã ghi',
          '${report['framesRecorded'] ?? 0}'),
      if (entries.isNotEmpty) ...[
        const Text('Các sự kiện gần nhất',
            style: TextStyle(fontWeight: FontWeight.bold)),
        for (final entry in entries.reversed.take(8))
          if (entry is Map)
            Text('• ${entry['event'] ?? '?'}'
                '${entry['details'] == null ? '' : ' · ${entry['details']}'}',
                style: Theme.of(context).textTheme.bodySmall),
      ],
    ]);
  }
}
