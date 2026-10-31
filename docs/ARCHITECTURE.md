# Kiến trúc M0–M1 (2026-10-31)

- Flutter `lib/app/app.dart`: điện thoại chỉ Settings với 5 tab và preview giới hạn trong khung AspectRatio, không dùng làm dashboard điều hướng.
- `lib/features/car_layout/`: schema v1, lưu SharedPreferences (giai đoạn thử nghiệm); Kotlin đọc từ `FlutterSharedPreferences` key `flutter.car_layout_config_v1` trong giai đoạn tiếp theo. Platform channel configChanged hiện chỉ phát tín hiệu thông báo.
- `lib/features/car_preview/`: mô phỏng có nhãn, không phải bản đồ có dữ liệu.
- `lib/features/payments/vietqr.dart`: EMV TLV/CRC16.
- `lib/features/speed_rules/`: rule thuần Dart, phân biệt nguồn.
- Android `SmartCarAppService` + `CarDiagnosticSurface`: app projection qua host template; surface vẽ thông báo kiểm thử, chưa phải bản đồ.
- Native package hợp lệ `vn.g98g00107.thanhtaxixanh.driver` do Kotlin không cho phần namespace bắt đầu bằng số.

Thiết kế chính: Android Auto surface/template là nguồn chân lý về viewport; 934×844 chỉ là kích thước design. Không có mã sao chép từ GPL upstream ở M0–M1.
