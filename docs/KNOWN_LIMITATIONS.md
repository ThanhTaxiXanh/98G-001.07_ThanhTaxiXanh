# Giới hạn

Phiên bản M3 có MapLibre OSM snapshot và nhập dữ liệu maxspeed GeoJSON theo vùng; chưa được kiểm tra Android Auto trên xe thật. Chưa có routing, cơ sở biển báo/camera toàn quốc, lịch nhắc tự động, audio engine, SQLite hay port YouTube. Surface xe là bản đồ 2D nhưng CHƯA PHẢI dashboard 2 cột 3 hàng tùy biến. Android Auto không cho đặt tự do widget Flutter 2 cột/3 hàng. Chưa có bằng chứng kết nối xe thật. QR phải quét kiểm thử thực; số tài khoản và BIN người dùng tự nhập. Luật tốc độ cần tái kiểm tra trước phát hành. Không tích hợp KingInstaller hoặc bypass an toàn.

M2: sự kiện cá nhân có CRUD lưu JSON trong SharedPreferences (`calendar_custom_events_v1`), hỗ trợ ngày âm/dương và tính lần tới; **chưa phải SQLite**, chưa có nhắc việc tự động hoặc migrations. Bộ ngày lễ mặc định được mở rộng theo `Events.txt` của người dùng.


M2 OSM snapshot map: renderer is implemented but Android Auto host rendering is PENDING_DEVICE_TEST. Internet connection is required for uncached tiles; MapLibre tile cache is not an offline region. GPS needs permission on phone. Speed limit remains UNKNOWN until verified sign/OSM road matching is connected. CAR_EXACT_DASHBOARD_SUCCESS remains BLOCKED_BY_PLATFORM.
