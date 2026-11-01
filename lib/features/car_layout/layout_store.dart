import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'layout_config.dart';

/// Legacy prefs intentionally used: the native service reads
/// FlutterSharedPreferences / flutter.car_layout_config_v1.
class LayoutStore extends ChangeNotifier {
  LayoutStore({CarLayoutConfig initial = const CarLayoutConfig()})
      : config = initial;

  static const _key = 'car_layout_config_v1';
  static const _channel =
      MethodChannel('vn.g98g00107.thanhtaxixanh.driver/config');
  CarLayoutConfig config;
  bool loaded = false;

  Future<void> load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString(_key);
      if (raw != null) config = CarLayoutConfig.decode(raw);
    } catch (e) {
      debugPrint('Layout load fallback: $e');
    }
    loaded = true;
    notifyListeners();
  }

  Future<void> update(CarLayoutConfig next) async {
    config = next;
    notifyListeners();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, next.encode());
    try {
      await _channel.invokeMethod<void>('configChanged', next.toJson());
    } on MissingPluginException {
      // Widget tests/non-Android host.
    } on PlatformException catch (e) {
      debugPrint('Native config notification: $e');
    }
  }
}
