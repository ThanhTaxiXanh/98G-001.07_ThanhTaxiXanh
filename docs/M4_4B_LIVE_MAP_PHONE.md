# M4.4B — MapLibre động trong bản xem trước điện thoại

**Phạm vi:** Android phone Flutter UI; **không thay đổi** `SmartCarAppService` và `CarOsmMapSurface` của Android Auto. Đây là bản thử nghiệm hiệu năng, chưa phải bản đồ điều hướng dẫn đường và chưa chứng minh có thể vẽ một Dashboard gồm Lịch/VietQR/Media lên host Android Auto.

## Kiến trúc

```text
Phone / Tab Bố cục
  SwitchListTile: MapLibre động (mặc định OFF)
    CarPreview 12×12 / MAP tile có kích thước thật
      LiveMapTile / AndroidView
        Flutter PlatformView registry, PhoneMapPreviewFactory
          MapLibre MapView (TextureView, OpenGL ES 13.7.1)
            Style OpenFreeMap liberty / OSM (cần Internet)
            GPS LocationManager, camera bearing/zoom/tilt 0° hoặc 48°
          FPS: MapView.OnDidFinishRenderingFrameListener
          Java Heap: Runtime.totalMemory/freeMemory
          Native Heap: Debug.getNativeHeapAllocatedSize
        MethodChannel phoneMapPreviewCommand
          status, set3d, zoomIn, zoomOut, recenter, refreshGps
```

- File native: `android/app/src/main/kotlin/vn/g98g00107/thanhtaxixanh/driver/PhoneMapPreview.kt`. MainActivity đăng ký `phone-map-view` và truyền Android Activity lifecycle sang MapView. PlatformView được dispose khi tắt/tách tab, ngừng LocationManager và hủy MapView.
- File Flutter: `lib/features/car_preview/live_map_tile.dart` sử dụng `AndroidView`, gesture recognizers để pan/pinch/rotate và overlay trạng thái; `car_preview.dart` dùng đúng kích thước LayoutBuilder thực thay vì scaling View Android bằng FittedBox.
- Khi tắt, preview trả ô bản đồ minh họa (không hiển thị giả GPS). Bản đồ động **không khởi tạo trong background**; ô Lịch, VietQR và Media vẫn do Flutter vẽ.
- Quản lý GPS: chỉ dùng vị trí hợp lệ không quá 30 giây, độ chính xác <=100 m khi có accuracy; hiển thị tốc độ nếu GPS còn mới; không gán bản đồ tổng quan Việt Nam là vị trí người dùng. Theo heading GPS khi có hướng đáng tin cậy và xe đang chuyển động; zoom 3–19, góc nghiêng 0/48 độ. Tên "3D" chỉ là camera pitch, không đảm bảo mô hình nhà 3D.
- Nguồn nền trực tuyến: https://tiles.openfreemap.org/styles/liberty. SQLite OSM maxspeed M4.3 vẫn hoạt động độc lập ở car; M4.4B **không** tự cung cấp lớp bản đồ offline.
- Android Auto tiếp tục MapSnapshotter → SurfaceCallback; không dùng `VirtualDisplay` trong car ở mốc này. Tách giới hạn Android for Cars: https://developer.android.com/training/cars/apps/library/draw-maps .

## Thử trên điện thoại Android

1. Cài debug APK từ workflow M4.4B **sau khi CI SUCCESS**. Trên tab **Bố cục**, bật **Bật bản đồ MapLibre động (điện thoại)**.
2. Cho phép Internet và GPS nếu muốn theo vị trí thực. Nếu tắt quyền GPS, bản đồ hiển thị tổng quan Việt Nam với nhãn chờ GPS, không giả tạo speed. Có nút làm mới GPS.
3. Dùng thao tác hai ngón để phóng to/xoay; bấm +, -, GPS. Bật/tắt **Góc nhìn nghiêng 3D (48°)** để kiểm tra camera. Cấu hình nghiêng được lưu trong LayoutStore; công tắc chạy bản đồ chỉ ở trạng thái phiên làm việc (không tự bật khi mở app).
4. Xem dòng *FPS gần đây*, *Java MB*, *Native MB*. **FPS = số frame trong cửa sổ khi có frame render**, lúc bản đồ đứng im FPS có thể bằng 0 và không có nghĩa ứng dụng lỗi. Java/native heap là bộ nhớ đã cấp phát trong runtime, không đồng nghĩa RAM tổng tiến trình hoặc GPU.
5. Chuyển qua tab Bản đồ, trở về tab Bố cục; tắt map bằng công tắc; lặp lại 10–20 lần. Kiểm tra crash, FPS, RAM, GPS bắt lại và pin sau 10/30 phút. Dữ liệu benchmark chỉ được xem là đo thực tế sau khi người dùng gửi thông số/chụp màn hình.
6. Khi dùng Android Auto trên xe: không thao tác cấu hình khi đang lái; mở app trong chế độ đỗ xe, xác nhận car MapSnapshotter vẫn render và hành vi host không thay đổi.

## Tiêu chí nghiệm thu và giới hạn

- Flutter analyze + unit/widget tests + native Kotlin/JUnit + debug APK CI xanh.
- Thử MapLibre MapView trên Android thực; CI chỉ compile, không chạy graphics/GPS/hardware acceleration instrumentation trên xe.
- Lifecycle: không còn GPS listener/MapView sau tắt/chuyển tab; không hai MapView đồng thời; onPause/onStop đóng render.
- Kiểm tra thấp nhất: không crash sau 20 lần bật/tắt, zoom/bearing/tilt giữ hợp lý, không tăng heap liên tục qua các lượt. Chưa có mức FPS/RAM ngưỡng được xác nhận nên **không ghi PASS hiệu năng trước khi đo**.
- Trường hợp MapLibre style không tải được khi mất mạng: preview giữ placeholder tĩnh khi tắt map; overlay hiển thị trạng thái map/GPS khi bật. Chưa có bản đồ nền ngoại tuyến.

**Tiếp theo:** M4.4C thu thập Android Auto DHU/host dimensions và validate Surface safe area; nếu cần engine động ở car, triển khai **sau khi** có số đo FPS/heap GPU và kiểm thử an toàn trên host. M5.1 vẫn là hệ thống dữ liệu biển báo/camera nguồn hợp lệ, không trộn lẫn với bản đồ giao diện.
