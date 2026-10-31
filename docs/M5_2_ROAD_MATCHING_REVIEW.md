# M5.2 — Ghép điểm biển báo với đường: bản thử nghiệm an toàn

Ngày 31/10/2026. Tiếp nối M5.1, mô-đun Kotlin `TrafficPointRoadMatcher` là bộ phân tích **thuần hàm** dành cho kiểm duyệt dữ liệu trên điện thoại, không kết nối với Android Auto và không cấp quyền bật cảnh báo.

## Đầu vào và thuật toán

- Candidate: id, tọa độ WGS84, tùy chọn `wayId` và `bearing` từ nguồn.
- Segment: id, polyline WGS84 và hướng lưu hình học (+1 thuận, -1 ngược, 0 hai chiều/chưa biết).
- Fix: GPS và độ chính xác ngang, hướng xe nếu có.
- Tính khoảng cách vuông góc tới từng cạnh polyline theo mặt phẳng tiếp tuyến cục bộ; chỉ xem xét điểm trong phạm vi 30 m.
- Khi có hướng GPS, loại đường một chiều lệch trên 45°. Đường hai chiều cho phép hai hướng.
- Nếu hai đường gần nhất chênh khoảng cách dưới 12 m, gắn `AMBIGUOUS_PARALLEL_ROADS`.
- Nếu `wayId` nguồn không trùng ID đoạn đường gần nhất, gắn `SOURCE_WAY_ID_MISMATCH`.
- Nếu thiếu heading hoặc GPS accuracy trên 15 m, gắn `INSUFFICIENT_GPS_EVIDENCE`.
- Nếu nguồn cung cấp bearing biển báo, **không** suy ra đó là chiều xe; gắn `SIGN_BEARING_SEMANTICS_UNREVIEWED`.
- Còn lại gắn `NEEDS_FIELD_VERIFICATION`.

Mọi kết quả đều là `UNVERIFIED`. Chỉ số khoảng cách gần **không phải xác suất chính xác**, và không dùng để quyết định tốc độ tối đa.

## Giới hạn quan trọng

Đây là thuật toán hình học ứng viên, **chưa** sử dụng graph OSM có turn restrictions, map matching theo chuỗi GPS, cầu vượt/hầm, làn đường, chiều biển theo quy chuẩn, thời gian hiệu lực, biển phụ hoặc xác nhận bằng ảnh khảo sát. Chưa có API kết nối SQLite M5.1 vào bộ matcher, chưa có giao diện duyệt từng điểm. Cũng chưa có tính năng phát cảnh báo âm thanh, camera hay giới hạn tốc độ từ các điểm này. Không dùng để dẫn đường thực tế.

## Kiểm thử

`TrafficPointRoadMatcherTest`: đường gần, đường song song, ngược chiều một chiều, wayId không trùng, bearing chưa rõ ngữ nghĩa, GPS thiếu/chất lượng thấp, biển ở xa và tọa độ không hợp lệ. Kết quả CI cần được kiểm tra trên commit cuối cùng.

## Hướng tiếp theo

M5.2B: thêm API truy vấn SQLite + bbox của đoạn đường, danh sách điểm cần duyệt trên điện thoại và cơ chế review với bằng chứng nguồn/ảnh, nhưng không cho tệp tự đặt `VERIFIED`. M5.3: ghép chuỗi GPS theo đồ thị có hướng, kiểm tra hiệu lực biển trên đoạn đường và kiểm thử GPX thực địa trước khi cân nhắc cảnh báo Android Auto.
