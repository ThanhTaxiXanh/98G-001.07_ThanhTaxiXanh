# Đối chiếu AALauncherGPT với ThanhTaxiXanh Smart Auto V5

Thời điểm xem xét: 2026-10-31. Nguồn chính: repo `ThanhTaxiXanh/AALauncherGPT`, nhánh `main`; đối chiếu mã nguồn V5 trên nhánh `feat/smart-auto-v5-m0-m1`. Đây là rà soát **mã**, không phải bằng chứng đã chạy thử trên VF e34.

## 1. Tỷ lệ và bố cục

- `dashboard/DashboardLayout.kt`: `DashboardTile(panel,x,y,width,height)` trong lưới 12×12. `ICONS` cố định (0,0,1,12). `MAP` tối thiểu 4×5; CALENDAR/QR/SIGNS tối thiểu 2×2; cấm chồng lấn và vượt lưới. Có tám preset và custom layout.
- `dashboard/LauncherRenderer.kt` dòng khoảng 177–215: đổi ô lưới ra pixel theo `pad = min(width,height)*0.013`, `cellW/H = usable/12`, inset 0.42×pad; **đúng một bộ hình chữ nhật cho render và hit test**.
- `dashboard/DashboardLayoutPreview.kt`: kéo thả / resize ở điện thoại, từ chối layout không hợp lệ; `DashboardSettings.kt`: lưu qua SharedPreferences JSON `payload_v1`.
- Ảnh màn hình do người dùng cung cấp trông gần mô hình biểu tượng 1/12, bản đồ 6/12, tiện ích bên phải 5/12, chiều cao bên phải khoảng Lịch 2/12 – QR 8/12 – tốc độ 2/12. **Chỉ là ước lượng thị giác**; ảnh không cho biết preset thực sự. `stacked` của mã có tỷ lệ 4/12–4/12–4/12 và `classic` không giống hoàn toàn ảnh. Media không là tile của `DashboardPanel`; ô cuối trong repo là SIGNS.

## 2. Cách bản đồ ra màn hình

Đường hiển thị trong `AALauncherGPT`:
`NativeMap.start(w,h)` → `MapLibre MapView` đặt trong `Presentation` / `VirtualDisplay` → `ImageReader(RGBA_8888)` → `Bitmap.copyPixelsFromBuffer` → `LauncherRenderer.drawMain()` (`Canvas.drawBitmap` đúng rectangle ô bản đồ) → `CarService.LauncherScreen` → `SurfaceCallback.onSurfaceAvailable` và `NavigationTemplate`.

- `car/CarService.kt`: `AppManager.setSurfaceCallback`, callbacks cuộn/vuốt/pinch/tap, `renderNavigationSurface` cho map-only; `renderer.render` toàn Dashboard **chỉ khi debug + experimentalCarDashboard**.
- `map/NativeMap.kt`: `MapLibre` style `https://tiles.openfreemap.org/styles/liberty`, camera có tilt/bearing, map padding; bắt frame qua `ImageReader.acquireLatestImage`, màn hình ảo và bản đồ cần lifecycle cleanup.
- `dashboard/PhoneDashboardActivity.kt`: Activity fullscreen độc lập trên điện thoại có map ảo và Dashboard hoàn chỉnh; không phải một app window do host Android Auto tự cho phép.

## 3. Giới hạn Android Auto bắt buộc tôn trọng

- `app/src/main/AndroidManifest.xml` **không** khai báo CarAppService; `app/src/debug/AndroidManifest.xml` mới khai báo `NavigationTemplate`, `NAVIGATION_TEMPLATES`, `ACCESS_SURFACE` và category NAVIGATION. Release của repo hiện là phone-only.
- `docs/PHASE11A_QUALITY_COMPLIANCE.md`, `PHASE11B_DEVICE_TEST_MATRIX.md`: debug dashboard nhiều ô là bản engineering preview **không đáp ứng NF-2**, còn điều hướng từng chặng NF-1 chưa có; không phải ứng dụng Android Auto được duyệt.
- Android official: https://developer.android.com/training/cars/apps/library/draw-maps ; https://developer.android.com/docs/quality-guidelines/car-app-quality (NF-1, NF-2). Android Auto cung cấp map Surface, nhưng Navigation surface không phải Canvas giao diện dashboard tùy ý với QR/lịch/media.
- Ảnh màn hình xe là bằng chứng về hình ảnh, **không chứng minh** các nút chạm hoạt động, route/navigation chuẩn, app được Google Play duyệt, hoặc mọi host AA đều hỗ trợ.

## 4. Đối chiếu với V5 và quyết định triển khai

| Hạng mục | AALauncherGPT | V5 hiện có | Hướng triển khai đề nghị |
| --- | --- | --- | --- |
| Bố cục | Lưới 12×12, 8 preset, custom | Preview 60/40 + 25/45/30 | Đưa lưới 12×12 vào **preview điện thoại trước** và thêm preset 1/6/5 + 2/8/2, không ghi đè cấu hình hiện hữu |
| Bản đồ | MapView động qua VirtualDisplay/ImageReader/Bitmap | MapSnapshotter → host Surface | Thử engine map động bằng feature flag debug, đo FPS/RAM/pin, giữ MapSnapshotter làm fallback |
| Android Auto | `NavigationTemplate`, map-only chính thức; multi-tile **debug opt-in** | Navigation Surface + MapSnapshotter | Giữ bản car host mặc định **map-only**; multi-tile chỉ cho thí nghiệm debug nếu có DHU/device evidence |
| Cảm ứng | Phân phối tap / scroll / scale từ callback | V5 hiện chủ yếu GPS và zoom điều khiển | Bổ sung hit-test, gesture và kiểm thử tap/scroll/pinch; ưu tiên safe/stable area |
| Tiện ích | Lịch, QR, SIGNS; Media3 là service khác | Preview Lịch, VietQR, Media | **Không đánh đồng** SIGNS với Media. Media thuộc host/session, không vẽ tùy ý lên Navigation Surface |
| Dữ liệu | OSM graph/cache/road match đa tầng | SQLite RTree + kiểm tra/dọn trùng M4.3 | Giữ data store M4.3; đánh giá engine map độc lập với nguồn maxspeed |

## 5. Kế hoạch triển khai sau rà soát

1. **M4.4A**: thuần logic `Grid12Layout` và tám preset + preset gần ảnh, kiểm tra chồng lấn, bounds, cực trị màn hình; hiển thị preview trên điện thoại và giữ migration cấu hình cũ.
2. **M4.4B**: thử MapLibre map động trong riêng preview điện thoại, đo khung hình/CPU/RAM và hoàn thiện thao tác GPS/zoom/recenter; không thay thế renderer ô tô trước khi có đo trên thiết bị.
3. **M4.4C**: DHU Android Auto debug: đo surface, stable/visible areas; kiểm thử map-only mặc định. Nếu tạo dashboard đa ô để **thử nghiệm**, phải gắn debug-only explicit và ghi noncompliant, không bật trong release. Thử trên VF e34 mới xác nhận khả thi theo host.
4. **M5.1**: hệ thống báo cáo vị trí biển báo/camera nguồn OSM/người dùng với tọa độ, timestamp, trạng thái xác minh; không hiển thị tin chưa xác minh như biển báo chính thức.

### Code nguồn tham khảo
- https://github.com/ThanhTaxiXanh/AALauncherGPT/blob/main/app/src/main/java/Thanh/TaxiDriver/AALauncher/dashboard/DashboardLayout.kt
- https://github.com/ThanhTaxiXanh/AALauncherGPT/blob/main/app/src/main/java/Thanh/TaxiDriver/AALauncher/dashboard/LauncherRenderer.kt
- https://github.com/ThanhTaxiXanh/AALauncherGPT/blob/main/app/src/main/java/Thanh/TaxiDriver/AALauncher/map/NativeMap.kt
- https://github.com/ThanhTaxiXanh/AALauncherGPT/blob/main/app/src/main/java/Thanh/TaxiDriver/AALauncher/car/CarService.kt
- https://github.com/ThanhTaxiXanh/AALauncherGPT/blob/main/docs/PHASE11A_QUALITY_COMPLIANCE.md
