# Giới hạn

Prototype M0/M1 chưa có renderer MapLibre, OSM routing, dữ liệu biển báo, camera, lịch âm/sự kiện ổn định, audio engine, SQLite hay port YouTube. Surface Android Auto là màn hình chẩn đoán (KHÔNG là map). Android Auto không cho đặt tự do widget Flutter 2 cột/3 hàng. Chưa có bằng chứng kết nối xe thật. QR phải quét kiểm thử thực; số tài khoản và BIN người dùng tự nhập. Luật tốc độ cần tái kiểm tra trước phát hành. Không tích hợp KingInstaller hoặc bypass an toàn.

M2: sự kiện cá nhân có CRUD lưu JSON trong SharedPreferences (`calendar_custom_events_v1`), hỗ trợ ngày âm/dương và tính lần tới; **chưa phải SQLite**, chưa có nhắc việc tự động hoặc migrations. Bộ ngày lễ mặc định được mở rộng theo `Events.txt` của người dùng.
