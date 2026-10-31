# Quy tắc ước tính tốc độ mặc định (kiểm tra pháp luật trước release)

Nguồn được kiểm tra 2026-10-31: https://chinhphu.vn/?classid=1&docid=211873&orggroupid=4&pageid=27160 ghi Thông tư 38/2024/TT-BGTVT có hiệu lực 01/01/2025. Chưa chứng minh chắc chắn không có văn bản sửa đổi/thay thế sau ngày đó: **REVERIFY_BEFORE_RELEASE**.

Xe con thông thường ngoài đường cao tốc: trong khu dân cư đường không dải phân cách = 50, đường đôi hoặc một chiều từ 2 làn xe cơ giới = 60; ngoài khu dân cư tương ứng 80/90. Tham chiếu Điều 6. Quy tắc chỉ ước tính khi có nguồn xác minh vùng pháp lý và hình thái đường. OSM road=urban, xây dựng dày, lanes=4 KHÔNG đủ để chứng minh dân cư hay dải phân cách. Cao tốc/xe khác/thiếu thông tin => UNKNOWN.

Mã `lib/features/speed_rules/speed_rules.dart`; 13 kiểm thử gồm phân cách, một chiều, cao tốc, biển được xác minh, OSM, thiếu dữ kiện. Đầu ra `≈50` và `SpeedSource.legalDefaultEstimate`, không phải biển chính thức. Không được khẳng định tài xế được chạy ở tốc độ ước tính.


## M3 — Nhập OSM maxspeed GeoJSON trên điện thoại Android

- Phone Settings > Bản đồ > Nhập GeoJSON từ điện thoại. Picker dùng ACTION_OPEN_DOCUMENT, KHÔNG cần root, quyền quản trị tệp hoặc máy tính.
- Chỉ chấp nhận chuẩn GeoJSON FeatureCollection có geometry LineString hoặc MultiLineString và properties chứa maxspeed, maxspeed:forward, maxspeed:backward theo đơn vị km/h; các giá trị "signals", "walk", "VN:urban", mph, điều kiện theo giờ/thời tiết bị bỏ qua để tránh hiểu sai.
- Tệp được giới hạn 8 MB / 50.000 segment: dùng cho từng khu vực nhỏ, không phải cơ sở dữ liệu tốc độ toàn quốc. Nhập dữ liệu sai/tệp quá lớn không làm mất cơ sở hiện hành.
- Engine gần GPS dùng khoảng cách đến segment, hướng di chuyển, one-way, sai số vị trí <=18 m và kiểm tra mâu thuẫn giữa đường song song. Nếu thiếu hướng cho maxspeed theo hướng hoặc nhiều đường có speed khác nhau trong sai số: trả UNKNOWN.
- Mọi speed hiển thị dạng **OSM: X km/h (tham khảo)**, KHÔNG là giới hạn biển báo đã xác minh. Xe phải tuân thủ biển báo và quy định tại thực địa; dữ liệu có thể cũ.
- Xuất vùng dữ liệu phù hợp từ https://overpass-turbo.eu (OSM tags highway và maxspeed). Giữ nguyên tọa độ WGS84 [lon, lat]. Không nhập ảnh/dataset YOLO biển báo như tọa độ.
- Import đọc file và xác thực nội dung trên luồng nền. Car map tải snapshot khi khởi động; sau cập nhật hãy ngắt/kết nối lại Android Auto.
- CI kiểm thử matcher bản địa bằng Gradle JUnit. Chưa đo hiệu năng trên xe; M3 này không đáp ứng bài toán đầy đủ đường/camera/biển báo Việt Nam.


## M4 — SQLite RTree (native Android)

`OsmRoadSqliteStore.kt`: schema `roads`, `road_rtree`, `metadata` sử dụng SQLiteOpenHelper, transactional replace; rtree `min_lat,max_lat,min_lon,max_lon` giới hạn vùng truy vấn quanh GPS 32m. Query có trần 128 ứng viên; vượt trần trả UNKNOWN. Kết quả vẫn đi qua `OsmRoadMatcher` M3 để xét hướng, sai số và mâu thuẫn. `MainActivity` gọi import/status/clear trên thread nền; CarSurface đọc gần GPS trên executor, không nạp toàn bộ dataset vào RAM. Có migration **một lần** từ file `osm_maxspeed_v1.geojson` của M3, chỉ xóa file cũ sau khi commit DB thành công. Khi cần đổi tệp, thao tác xóa/nhập là giao dịch không tự tạo speed mới. Hạn mức tệp M4 **vẫn 8MB / 50.000 segments**; SQLite RTree là tối ưu truy vấn, không có nghĩa đã hỗ trợ toàn quốc. Cần parser streaming + phân vùng và kiểm thử thiết bị trước khi tăng hạn mức. `Robolectric` kiểm thử index, transactional replacement và status trên Android giả lập trong Gradle CI. Chưa có GPS thực tế/xe thật.

M4.2: nâng schema DB từ 1 lên 2, thêm `road_fingerprints(fingerprint TEXT PRIMARY KEY, road_id)` và backfill từ dữ liệu M4.1 trong giao dịch nâng cấp. Dữ liệu nhập bổ sung lọc trùng hình học + thuộc tính maxspeed/oneway bằng SHA-256; cùng hình học mà tốc độ mâu thuẫn thì giữ để trả UNKNOWN. Lọc từng đoạn trong SQLite transaction, số lượng ghi thực tế phải không vượt trần 1.000.000; không dùng số segment đầu vào (chứa đoạn trùng) để từ chối trước hạn mức. Legacy duplicates không tự xóa trong migration nhằm bảo toàn dữ liệu người dùng. Bản CI M4.1 đã đạt; M4.2 chưa test xe thật.
