# THANHTAXIXANH SMART AUTO — V5

**Mốc đang triển khai: M4.2 (prototype; M4.1 CI/APK đã đạt, M4.2 đang kiểm thử).** Android Auto projection là đích hiển thị chính; điện thoại Android chỉ **Cài đặt + Xem trước thiết kế**. Không gọi preview trên điện thoại là dashboard thực tế trên xe.

- Repo: ThanhTaxiXanh/98G-001.07_ThanhTaxiXanh
- Kotlin namespace / Android applicationId: `vn.g98g00107.thanhtaxixanh.driver`
- Flutter SDK pinned: `3.47.0`; Android Car App Library `1.7.0`.
- Layout **mục tiêu**: 934×844 px; bản đồ 60%; Lịch 25%, VietQR 45%, Media 30% trên cột phải.
- Điện thoại: 5 trang Settings; preview mô phỏng, slider điều chỉnh live, lưu cấu hình.
- Android Auto: CarAppService + NavigationTemplate + MapLibre/OpenFreeMap snapshot SurfaceCallback, GPS, và matcher tốc độ OSM từ tệp GeoJSON nhập theo vùng. Chưa xác minh hoạt động trên xe thật, chưa có dashboard 2 cột 3 hàng hoạt động thực.
- VietQR: payload offline EMVCo + CRC16; chỉ tạo QR khi người dùng nhập thông tin hợp lệ, không có tài khoản ngân hàng hard-code.
- Speed rules: 50/60/80/90 **ước tính** có điều kiện, không hiển thị biển ảo. Phải kiểm tra lại pháp luật trước phát hành.

## Nhập dữ liệu tốc độ OSM bằng điện thoại Android

Xem [hướng dẫn xuất GeoJSON trên điện thoại](docs/OSM_GEOJSON_PHONE_GUIDE.md). M4.2 dùng SQLite RTree/B-tree, cho phép nhập nối tiếp tối đa 1.000.000 đoạn từ các tệp GeoJSON theo vùng (8 MB / 50.000 đoạn mỗi tệp); nhận diện đoạn trùng theo hình học và maxspeed, không xóa các giá trị tốc độ xung đột. Không có dữ liệu thì hiển thị --, không tạo biển báo giả.

## Build, test, APK

GitHub Actions [CI](.github/workflows/ci.yml) chạy format, Flutter analyze/test, build APK và Gradle JUnit cho mô-đun tốc độ OSM. Hướng dẫn iPhone: [BUILD_ON_IPHONE.md](docs/BUILD_ON_IPHONE.md). Trình tự build giai đoạn này tạo các tệp scaffold Flutter Android còn thiếu qua `flutter create` mà không ghi đè mã nguồn đã commit.

## Định nghĩa trạng thái

- `PHONE_SETTINGS_ONLY`: cần CI và kiểm thử cài đặt.
- `PHONE_PREVIEW_934X844`: có implementation, còn chờ render/golden test.
- `AA_NAV_DISCOVERED`: PENDING_DEVICE_TEST.
- `AA_CAR_SURFACE`: MapLibre renderer implemented / PENDING_DEVICE_TEST.
- `AA_EXACT_2COL_3ROW`: BLOCKED_BY_PLATFORM cho thiết kế tùy ý; cần kiểm chứng riêng nếu host có capability phù hợp.

Mọi giới hạn và tiến độ trong [docs/FEATURE_MATRIX.md](docs/FEATURE_MATRIX.md), [docs/CAR_DASHBOARD_FEASIBILITY.md](docs/CAR_DASHBOARD_FEASIBILITY.md).
