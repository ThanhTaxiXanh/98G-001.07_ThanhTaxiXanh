import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// M5.1 candidate data management on the PHONE, not a car-driving warning UI.
class TrafficPointPanel extends StatefulWidget {
  const TrafficPointPanel({super.key});

  @override
  State<TrafficPointPanel> createState() => _TrafficPointPanelState();
}

class _TrafficPointPanelState extends State<TrafficPointPanel> {
  static const channel =
      MethodChannel('vn.g98g00107.thanhtaxixanh.driver/config');

  Map<String, dynamic> status = const {};
  bool busy = false;
  String? error;

  @override
  void initState() {
    super.initState();
    unawaited(_request('getTrafficPointStatus'));
  }

  Future<void> _request(String method, {Map<String, dynamic>? args}) async {
    if (busy) return;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final value = await channel.invokeMapMethod<String, dynamic>(method, args);
      if (mounted && value != null) {
        setState(() => status = Map<String, dynamic>.from(value));
      }
    } on MissingPluginException {
      if (mounted) {
        setState(() => error = 'Chức năng này chỉ chạy trên điện thoại Android.');
      }
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() => error = e.message ?? 'Không thể xử lý tệp GeoJSON.');
      }
    } catch (_) {
      if (mounted) {
        setState(() => error = 'Định dạng kết quả không hợp lệ.');
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> _confirmReplace() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Thay toàn bộ điểm đã nhập?'),
        content: const Text('Chỉ xóa cơ sở dữ liệu điểm biển báo/camera M5.1. '
            'Không ảnh hưởng cơ sở đoạn đường maxspeed M4, '
            'lịch hay VietQR. Tệp mới phải hợp lệ trước khi thay.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Chọn tệp thay thế'),
          ),
        ],
      ),
    );
    if (ok == true && mounted) {
      await _request('importTrafficPointGeoJson', args: {'append': false});
    }
  }

  Future<void> _confirmClear() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Xóa điểm biển báo/camera?'),
        content: const Text('Không thể hoàn tác việc xóa điểm M5.1. '
            'Dữ liệu đoạn đường maxspeed không bị xóa.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Xóa điểm'),
          ),
        ],
      ),
    );
    if (ok == true && mounted) await _request('clearTrafficPoints');
  }

  @override
  Widget build(BuildContext context) {
    final total = status['total'] ?? 0;
    final unverified = status['unverified'] ?? 0;
    final byKind = status['byKind'];
    final counts = byKind is Map ? byKind : const {};
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text('M5.1 • Kho tọa độ ứng viên biển báo, camera, trạm dịch vụ '
            '(SQLite ngoại tuyến). Tất cả điểm nhập được đánh dấu '
            'UNVERIFIED, không phát cảnh báo trên xe và không ghi đè maxspeed.'),
        const SizedBox(height: 8),
        Text('Tổng số điểm: $total • Chưa xác minh: $unverified',
            key: const Key('traffic-point-summary')),
        if (status['lastAdded'] != null)
          Text('Lần nhập: +${status['lastAdded']} điểm, '
              'bỏ qua ${status['lastSkipped'] ?? 0} điểm trùng'),
        if (counts.isNotEmpty)
          Text('Theo loại: ${counts.entries.map((e) =>
              '${e.key}: ${e.value}').join(' • ')}'),
        const Text('Tệp phải là GeoJSON FeatureCollection gồm Point '
            '[kinh độ, vĩ độ], properties: kind, source, license; '
            'speed_limit cần speedKmh. Giới hạn 5 MB / 10.000 điểm mỗi tệp. '
            'Nguồn OpenStreetMap phải tuân thủ ODbL.'),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            FilledButton.icon(
              key: const Key('traffic-point-import'),
              onPressed: busy
                  ? null
                  : () => unawaited(_request('importTrafficPointGeoJson',
                      args: {'append': true})),
              icon: const Icon(Icons.add_location_alt_outlined),
              label: const Text('Nhập bổ sung GeoJSON'),
            ),
            OutlinedButton.icon(
              key: const Key('traffic-point-refresh'),
              onPressed: busy
                  ? null
                  : () => unawaited(_request('getTrafficPointStatus')),
              icon: const Icon(Icons.refresh),
              label: const Text('Kiểm tra số điểm'),
            ),
            if (total is num && total > 0) ...[
              OutlinedButton.icon(
                onPressed: busy ? null : () => unawaited(_confirmReplace()),
                icon: const Icon(Icons.file_open),
                label: const Text('Thay toàn bộ'),
              ),
              TextButton.icon(
                onPressed: busy ? null : () => unawaited(_confirmClear()),
                icon: const Icon(Icons.delete_outline),
                label: const Text('Xóa các điểm đã nhập'),
              ),
            ],
          ],
        ),
        if (busy) const LinearProgressIndicator(),
        if (error != null)
          Text(error!, style: const TextStyle(color: Colors.orange)),
        const SizedBox(height: 5),
        const Text('Chưa có tọa độ được chứng nhận. Cảnh báo thực địa chỉ '
            'được kích hoạt ở mốc tiếp theo sau khi gắn đúng chiều đường, '
            'làn xe, hiệu lực biển và có bằng chứng kiểm duyệt.'),
      ],
    );
  }
}
