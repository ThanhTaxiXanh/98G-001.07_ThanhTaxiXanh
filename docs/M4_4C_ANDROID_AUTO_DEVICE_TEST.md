# M4.4C — Kiểm tra Android Auto trên VF e34 bằng điện thoại

**Mục tiêu:** lấy số đo thật do Android Auto host cung cấp về Surface, visible area và stable area; không đoán kích thước từ ảnh chụp hoặc cấu hình preview. Mốc này **không** bật Dashboard nhiều ô lên Android Auto.

## Đã triển khai trong mã nguồn

- `CarSurfaceViewport.compute`: cắt `visibleArea` và `stableArea` theo kích thước Surface, tính vùng giao nhau. Khi host không gửi inset hoặc hai vùng không giao nhau, bỏ HUD cảnh báo tốc độ khỏi vùng chưa được xác nhận thay vì vẽ đè toàn màn hình. Bản đồ OSM vẫn vẽ trên toàn Surface.
- `CarOsmMapSurface`: ghi các callback `onSurfaceAvailable`, `onVisibleAreaChanged`, `onStableAreaChanged`, `onSurfaceDestroyed`, các sự kiện cử chỉ mà host thực sự chuyển tiếp, cập nhật ảnh MapLibre thành công/thất bại và số lượt vẽ có giới hạn ghi đĩa. Callback pinch scale chỉ hỗ trợ zoom có giới hạn; scroll/fling/click mới ghi nhận, **chưa có pan/tap action thực**. Không bịa khả năng host phát callback.
- `CarSurfaceTelemetry`: báo cáo JSON nhỏ lưu bằng SharedPreferences trên điện thoại. Lưu kích thước và 40 sự kiện kỹ thuật gần nhất; **không** lưu tọa độ GPS, tốc độ cá nhân, số tài khoản hoặc payload VietQR. Có nút xóa nhật ký.
- `SmartCarAppService`: dừng map và bỏ callback Surface khi Screen lifecycle bị hủy, phòng trường hợp host không gửi event hủy Surface.
- `CarSurfaceDiagnosticsPanel`: trên điện thoại vào tab **Bản đồ → M4.4C · Chẩn đoán Android Auto trên xe**. Có nút **Đọc trạng thái Surface**, **Sao chép báo cáo** và **Xóa nhật ký**. Dữ liệu hiển thị là **lần quan sát cuối**, không phải chứng nhận xe hiện đang kết nối.

## Các bước kiểm tra trên VF e34 — không cần PC

1. Chỉ thực hiện khi **xe đang đỗ an toàn**. Tải APK từ GitHub Actions của commit M4.4C sau khi trạng thái CI SUCCESS, cài trên điện thoại Android và bảo đảm quyền truy cập vị trí nếu cần bản đồ GPS. Không cần root.
2. Kết nối Android Auto như bình thường. Mở ứng dụng ThanhTaxiXanh trên xe (nếu được host cho phép). Chụp ảnh màn hình xe khi bản đồ hiển thị toàn màn hình hoặc chia đôi; ghi lại phần nút zoom/GPS có nhìn rõ hay không.
3. Trên điện thoại mở ThanhTaxiXanh → **Bản đồ** → **Đọc trạng thái Surface**. Xem Surface Width × Height, DPI, tỷ lệ rộng/cao, Visible Area, Stable Area, Safe Area và sự kiện gần nhất. Nếu chưa có sự kiện, ghi nhận đúng là `Chưa ghi nhận`, không tự suy ra kích thước từ màn hình xe.
4. Khi xe vẫn đỗ, chuyển qua các chế độ màn hình host hỗ trợ (toàn màn hình, chia đôi, launcher), mở lại bảng chẩn đoán rồi bấm **Đọc trạng thái Surface**. Nếu visible/stable không thay đổi dù UI đổi, ghi nhận hạn chế host và gửi ảnh; không coi inset đã cũ là đúng.
5. Thử **Phóng to / Thu nhỏ** trong action strip và thao tác chụm phóng to trực tiếp trên host nếu host cho phép. Kiểm tra mục `Cử chỉ do host chuyển tiếp`. Chưa có điều khiển scroll/fling trong bản này.
6. Ngắt kết nối Android Auto, kết nối lại, rồi xem số lần Surface tạo/hủy và log. Nếu host không gửi destroy, Lifecycle Screen vẫn phải đóng map; quan sát kết quả thay vì kết luận trước.
7. Nhấn **Sao chép báo cáo** và dán JSON vào cuộc trò chuyện để kiểm tra. JSON không chứa tọa độ hay VietQR; tránh chia sẻ thêm màn hình có thông tin nhạy cảm nếu không cần.

## Cách đọc trạng thái

- `surfaceWidth`, `surfaceHeight`: kích thước pixel do Android Auto cung cấp, không nhất thiết bằng độ phân giải vật lý của màn VF e34.
- `surfaceDpi` và `surfaceAspectRatio`: giá trị từ chính SurfaceContainer của host, không lấy từ cấu hình màn hình điện thoại. Kích thước Surface cuối cùng được giữ lại trong báo cáo ngay cả khi Screen đã dừng.
- `visible`: vùng host hiện báo đang nhìn thấy. `stable`: vùng host báo ít bị UI động che phủ. `safe`: phần giao hợp lệ, dùng để đặt thông tin tốc độ.
- `overlaySafe`: true nghĩa tính toán dựa trên inset đã biết hợp lệ; **không đảm bảo** host không có lỗi cập nhật inset.
- `lastObservedAt`, `lastEvent`: thời điểm và sự kiện cuối. `lastObservedSurfaceActive` là trạng thái callback đã lưu, **không** phải chứng thực kết nối hiện thời.
- `framesRecorded`: số lần vẽ đã ghi định kỳ; không phải FPS. FPS/RAM của MapLibre **điện thoại** được đo trong M4.4B, không thể suy ra hiệu năng GPU/Surface Android Auto chỉ từ chỉ số này.
- `events`: tối đa 40 sự kiện gần nhất; chỉ lưu loại sự kiện và chú thích kỹ thuật.

## Kiểm thử/điểm chặn

- CI: Flutter analyze, widget test JSON/nút chẩn đoán; JUnit tính safe viewport, Robolectric nhật ký/bảo mật dữ liệu; APK debug và bản đồ OSM cũ.
- Device acceptance: bắt buộc xác nhận callback host khi mở/đóng app, split-screen, điều khiển zoom và việc vẽ không đè UI. **Chưa có thiết bị hoặc DHU thật được điều khiển trong CI.**
- Nếu host không báo inset hoặc báo vùng mâu thuẫn, ứng dụng cố ý ẩn HUD thay vì chồng lên nút hệ thống.
- Với Android Auto 1.7.0 đã có báo cáo công khai về visible/stable callback bị thiếu trong chế độ split screen; cần kiểm tra trên từng host. Nguồn: https://issuetracker.google.com/issues/471627946
- Google Car App Library docs: https://developer.android.com/reference/androidx/car/app/SurfaceCallback và https://developer.android.com/reference/androidx/car/app/Screen
- Car renderer mặc định vẫn là **NavigationTemplate + MapLibre MapSnapshotter**, không phải Dashboard tự do. Bản thân ứng dụng vẫn chưa có điều hướng từng chặng đủ điều kiện phát hành dưới loại Navigation.

**Bước sau M4.4C:** nhận báo cáo và ảnh VF e34, sửa sai vùng/gesture/lifecycle theo thiết bị thật. Tiếp theo mới xem xét M5.1 — thu thập dữ liệu biển báo/camera hợp lệ có tọa độ và trạng thái xác minh.
