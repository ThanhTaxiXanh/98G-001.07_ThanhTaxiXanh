# M5.3 — GPS trace road review prototype

Ngày 31/10/2026. Mã Kotlin `GpsTraceRoadReview` nhận danh sách điểm GPS theo thời gian và các đoạn đường có tọa độ, chiều hình học. Mỗi điểm được chiếu lên đoạn đường, lọc khoảng cách 30 m và sai lệch heading 45°, cộng hình phạt đổi đường 8 m để hạn chế nhảy giữa hai đường gần nhau.

**Bảo đảm an toàn:** kết quả `UNVERIFIED_REVIEW_ONLY`, không nối Android Auto, không phát cảnh báo biển/camera, không cập nhật tốc độ tối đa. Không có OSM Way ID, chưa có dữ liệu biển báo đã kiểm chứng. Không dùng để lái xe.

**Giới hạn:** thuật toán greedy, không phải Viterbi/HMM, chưa có graph topology, restrictions, cầu/hầm, chuyển hướng hợp lệ hoặc GPS motion model. Điểm GPS thiếu heading hoặc accuracy trên 25 m bị đánh dấu không chắc chắn. Nếu hai lựa chọn có điểm số cách nhau dưới 6 m, kết quả là null. Hình phạt đổi đường không chứng minh khả năng chuyển làn hoặc rẽ hợp lệ. Tối đa 5000 fixes và 5000 roads để giới hạn công việc.

**Kiểm thử mới:** trace liên tục, đường song song, đi ngược chiều đường một chiều, thiếu heading, sai số GPS và thời gian không tăng. Cần CI xanh trên commit cuối và kiểm thử GPX thực địa trước khi kết nối UI.

**M5.3B đề xuất:** lưu ID OSM Way + thứ tự nút/chiều khi nhập dữ liệu; xây graph đường có hướng và transition hợp lệ; đối chiếu GPX thực địa (không dùng mẫu giả để tuyên bố chính xác); đo tỉ lệ UNKNOWN, false matches và sai lệch giới hạn tốc độ.
