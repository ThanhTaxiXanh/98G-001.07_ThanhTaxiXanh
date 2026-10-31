# Android Auto projection — feasibility gate

Ngày xem xét: 2026-10-31. Độ phân giải 934x844 là pixel mục tiêu, không phải actual host viewport.

| Tiêu chí | Cơ chế | Trạng thái |
|---|---|---|
| Khởi chạy CarAppService | `SmartCarAppService.kt`, NavigationTemplate | PENDING_DEVICE_TEST |
| Ghi kích thước SurfaceContainer | `CarDiagnosticSurface.kt`, Android log | PENDING_DEVICE_TEST |
| Hiển thị bản đồ thực | Car App SurfaceCallback + MapLibre (chưa tích hợp) | NOT_IMPLEMENTED |
| 2 cột và 3 hàng widget tùy ý | NavigationTemplate/MapWithContentTemplate không cho phép layout Flutter tự do | BLOCKED_BY_PLATFORM |
| QR động bên phải | Chưa có host component tương đương do ứng dụng toàn quyền điều khiển | BLOCKED_BY_PLATFORM |
| Media controls đồng thời trong cùng 3-row template | Media được host render riêng | BLOCKED_BY_PLATFORM |
| Layout preview đúng 934x844 | `CarPreview` `AspectRatio` | IMPLEMENTED / TEST_PENDING |
| Tap, pinch, pan | SurfaceCallback có callbacks khi host cho phép | NOT_IMPLEMENTED |
| Biển báo, đóng góp trên car | Chưa có map/routing | NOT_IMPLEMENTED |

Nguồn chính thức:
- https://developer.android.com/training/cars/apps/navigation
- https://developer.android.com/training/cars/apps/library/draw-maps
- https://developer.android.com/training/cars/apps/library/interact-map
- https://developer.android.com/training/cars/testing

Ghi nhận khi kiểm thử xe thật: điện thoại/model ROM, Android Auto version, AA API, xe/host, `CAR_SURFACE` width x height, stable area, visible area, ảnh chụp car display. Không chấp nhận ảnh preview làm bằng chứng car dashboard.
