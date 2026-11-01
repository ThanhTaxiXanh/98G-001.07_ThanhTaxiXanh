# Kế hoạch nghiệm thu

CI: `dart format`, `flutter analyze`, `flutter test`, `flutter build apk --debug`. Tests: layout serialization/normalization, VietQR CRC/payload/validation, speed rules thirteen cases, Settings-only widget test. CI pass chưa đủ để đánh dấu pass trên xe.

Thiết bị: cài APK trên Android, kiểm tra 5 trang và lưu cấu hình sau restart, quét QR bằng ít nhất hai ứng dụng ngân hàng, mở Android Auto launcher, log CAR_SURFACE, chụp màn hình xe. Chỉ đánh dấu `CAR_LAUNCH_SUCCESS` khi host gọi CarAppService. `CAR_EXACT_DASHBOARD_SUCCESS` yêu cầu hình ảnh đồng thời map/Lịch/QR/Media thực, không phải bản mô phỏng. Mọi device case hiện PENDING_DEVICE_TEST.
