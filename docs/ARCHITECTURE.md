# Kiến trúc M0–M1 (2026-10-31)

- Flutter `lib/app/app.dart`: điện thoại chỉ Settings với 5 tab và preview giới hạn trong khung AspectRatio, không dùng làm dashboard điều hướng.
- `lib/features/car_layout/`: schema v1, lưu SharedPreferences (giai đoạn thử nghiệm); Kotlin đọc từ `FlutterSharedPreferences` key `flutter.car_layout_config_v1` trong giai đoạn tiếp theo. Platform channel configChanged hiện chỉ phát tín hiệu thông báo.
- `lib/features/car_preview/`: mô phỏng có nhãn, không phải bản đồ có dữ liệu.
- `lib/features/payments/vietqr.dart`: EMV TLV/CRC16.
- `lib/features/speed_rules/`: rule thuần Dart, phân biệt nguồn.
- Android `SmartCarAppService` + `CarDiagnosticSurface`: app projection qua host template; surface vẽ thông báo kiểm thử, chưa phải bản đồ.
- Native package hợp lệ `vn.g98g00107.thanhtaxixanh.driver` do Kotlin không cho phần namespace bắt đầu bằng số.

Thiết kế chính: Android Auto surface/template là nguồn chân lý về viewport; 934×844 chỉ là kích thước design. Không có mã sao chép từ GPL upstream ở M0–M1.


## M2 - OSM on Android Auto Surface

MapLibre Native OpenGL 13.7.1 draws snapshots from OpenFreeMap Liberty vector style onto the host-provided Surface. The renderer handles GPS updates with Android runtime permission, visible and stable rectangles, snapshot life-cycle, zoom +/- and explicit UNKNOWN speed limit. It does not invent a road speed limit. No route engine or full offline basemap is installed. Android Auto host launch and rendering on the vehicle remain UNVERIFIED. No Vela GPL source was copied; public MapLibre APIs were independently implemented. See https://maplibre.org/maplibre-native/android/examples/snapshotter/ and https://openfreemap.org/quick_start/ .
