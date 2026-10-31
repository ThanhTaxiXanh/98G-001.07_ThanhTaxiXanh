import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// MapLibre MapView embedded in PHONE preview only. The AA host is unchanged.
class LiveMapTile extends StatefulWidget {
  const LiveMapTile({super.key, required this.tilt3d});
  final bool tilt3d;
  @override
  State<LiveMapTile> createState() => _LiveMapTileState();
}

class _LiveMapTileState extends State<LiveMapTile>
    with WidgetsBindingObserver {
  static const channel =
      MethodChannel('vn.g98g00107.thanhtaxixanh.driver/config');
  static const viewType =
      'vn.g98g00107.thanhtaxixanh.driver/phone-map-view';

  Timer? timer;
  Map<String, dynamic> status = const {};
  bool created = false;
  bool busy = false;
  String? error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void didUpdateWidget(covariant LiveMapTile oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.tilt3d != widget.tilt3d && created) {
      unawaited(command('set3d', enabled: widget.tilt3d));
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && created) {
      startSampling();
    } else {
      timer?.cancel();
      timer = null;
    }
  }

  void startSampling() {
    timer?.cancel();
    unawaited(command('status'));
    timer = Timer.periodic(
      const Duration(seconds: 2),
      (_) => unawaited(command('status')),
    );
  }

  Future<void> command(String action, {bool? enabled}) async {
    if (!created || busy) return;
    busy = true;
    try {
      final data = await channel.invokeMapMethod<String, dynamic>(
        'phoneMapPreviewCommand',
        <String, dynamic>{'action': action, if (enabled != null) 'enabled': enabled},
      );
      if (mounted) {
        setState(() {
          status = Map<String, dynamic>.from(data ?? const {});
          error = null;
        });
      }
    } on MissingPluginException {
      if (mounted) setState(() => error = 'MapLibre chỉ có trên Android.');
    } on PlatformException catch (e) {
      if (mounted) setState(() => error = e.message ?? 'Lỗi điều khiển bản đồ');
    } finally {
      busy = false;
    }
  }

  @override
  void dispose() {
    timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
      return const ColoredBox(
        color: Color(0xff102c3e),
        child: Center(child: Text('Bản đồ động chỉ hỗ trợ điện thoại Android')),
      );
    }

    final gps = status['hasGps'] == true;
    final speed = status['speedKmh'] as int? ?? -1;
    final fps = (status['fps'] as num? ?? 0).toDouble();
    final heap = (status['javaHeapMb'] as num? ?? 0).toDouble();
    final nativeHeap = (status['nativeHeapMb'] as num? ?? 0).toDouble();
    final label = error ?? status['status'] as String? ?? 'MapLibre đang khởi tạo';
    return Stack(children: [
      Positioned.fill(
        child: AndroidView(
          viewType: viewType,
          onPlatformViewCreated: (_) {
            created = true;
            unawaited(command('set3d', enabled: widget.tilt3d));
            startSampling();
          },
          // Native map gets pan, rotate, and pinch; preview is intentionally opt-in.
          gestureRecognizers: <Factory<OneSequenceGestureRecognizer>>{
            Factory<EagerGestureRecognizer>(() => EagerGestureRecognizer()),
          },
        ),
      ),
      Positioned(top: 6, right: 6, child: Column(children: [
        _control(Icons.add, 'Phóng to', () => command('zoomIn')),
        _control(Icons.remove, 'Thu nhỏ', () => command('zoomOut')),
        _control(Icons.my_location, 'Về GPS', () => command('recenter')),
        _control(Icons.gps_fixed, 'Làm mới GPS', () => command('refreshGps')),
      ])),
      Positioned(left: 5, top: 5, right: 53,
        child: IgnorePointer(child: DecoratedBox(
          decoration: BoxDecoration(
            color: const Color(0xd9001828),
            borderRadius: BorderRadius.circular(7)),
          child: Padding(padding: const EdgeInsets.all(6),
            child: Text(label, maxLines: 2,
              style: const TextStyle(fontSize: 11, color: Colors.white))),
        ))),
      Positioned(bottom: 5, left: 5, right: 5,
        child: IgnorePointer(child: DecoratedBox(
          decoration: BoxDecoration(
            color: const Color(0xe0001828),
            borderRadius: BorderRadius.circular(7)),
          child: Padding(padding: const EdgeInsets.all(6),
            child: Text(
              (gps && speed >= 0 ? 'GPS $speed km/h' : 'GPS --') +
              ' • FPS gần đây ' + fps.toStringAsFixed(1) +
              ' • Java ' + heap.toStringAsFixed(0) + ' MB' +
              ' • Native ' + nativeHeap.toStringAsFixed(0) + ' MB',
              maxLines: 2,
              style: const TextStyle(fontSize: 10, color: Colors.white)),
          )))),
    ]);
  }

  Widget _control(IconData icon, String hint, Future<void> Function() action) =>
      Padding(
        padding: const EdgeInsets.only(bottom: 3),
        child: SizedBox(width: 35, height: 35,
          child: Material(color: const Color(0xe6002036),
            borderRadius: BorderRadius.circular(8),
            child: IconButton(
              padding: EdgeInsets.zero,
              tooltip: hint,
              icon: Icon(icon, size: 19, color: Colors.white),
              onPressed: () => unawaited(action()),
            )),
        ),
      );
}
