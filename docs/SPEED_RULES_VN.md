# Quy tắc ước tính tốc độ mặc định (kiểm tra pháp luật trước release)

Nguồn được kiểm tra 2026-10-31: https://chinhphu.vn/?classid=1&docid=211873&orggroupid=4&pageid=27160 ghi Thông tư 38/2024/TT-BGTVT có hiệu lực 01/01/2025. Chưa chứng minh chắc chắn không có văn bản sửa đổi/thay thế sau ngày đó: **REVERIFY_BEFORE_RELEASE**.

Xe con thông thường ngoài đường cao tốc: trong khu dân cư đường không dải phân cách = 50, đường đôi hoặc một chiều từ 2 làn xe cơ giới = 60; ngoài khu dân cư tương ứng 80/90. Tham chiếu Điều 6. Quy tắc chỉ ước tính khi có nguồn xác minh vùng pháp lý và hình thái đường. OSM road=urban, xây dựng dày, lanes=4 KHÔNG đủ để chứng minh dân cư hay dải phân cách. Cao tốc/xe khác/thiếu thông tin => UNKNOWN.

Mã `lib/features/speed_rules/speed_rules.dart`; 13 kiểm thử gồm phân cách, một chiều, cao tốc, biển được xác minh, OSM, thiếu dữ kiện. Đầu ra `≈50` và `SpeedSource.legalDefaultEstimate`, không phải biển chính thức. Không được khẳng định tài xế được chạy ở tốc độ ước tính.
