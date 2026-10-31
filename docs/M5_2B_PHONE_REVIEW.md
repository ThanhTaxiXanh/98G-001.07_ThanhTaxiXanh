# M5.2B — Duyệt ứng viên trên điện thoại (31/10/2026)

Đã nối kho SQLite `traffic_points_m5.sqlite` với bộ ghép hình học M5.2 và màn hình Bản đồ → M5.1/M5.2B. Nhập **vĩ độ/kinh độ** để xem tối đa 50 ứng viên gần vị trí trong cửa sổ 0,002 độ, kèm nguồn, giấy phép, loại điểm và lý do không thể kết luận.

**Không phải kiểm duyệt chính thức.** `UNVERIFIED` được giữ nguyên, không có nút xác minh, không cập nhật giới hạn tốc độ trên xe. Không dùng khi đang lái xe.

**Giới hạn quan trọng:** CSDL M4 chỉ có các đoạn hình học và ID nội bộ, không lưu OSM Way ID. Vì vậy bộ nối đặt ID tạm `M4_ROW_UNLINKED`, không giả định đây là `wayId` thực. Hiện chưa truyền hướng GPS từ điện thoại, nên lý do kiểm tra có thể là `INSUFFICIENT_GPS_EVIDENCE` hoặc `SOURCE_WAY_ID_MISMATCH`. Kết quả khoảng cách chỉ có giá trị gợi ý vị trí, không chứng nhận biển áp dụng cho đường/làn nào. Bộ truy vấn M4 có giới hạn 128 đoạn; nếu quá tải sẽ trả về `ROAD_QUERY_OVERFLOW_OR_UNAVAILABLE`.

Bước tiếp: bổ sung ID OSM Way bền vững trong pipeline nhập đường, dữ liệu chiều biển, kiểm duyệt có bằng chứng và kiểm thử GPX trên đường thực tế.
