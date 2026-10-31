# Xuất GeoJSON maxspeed OSM và nhập trên điện thoại Android (M3)

Không cần máy tính hoặc quyền root. GeoJSON là dữ liệu đường có tọa độ và tag maxspeed, KHÔNG phải GPX hoặc ảnh biển báo.

## A. Xuất dữ liệu OSM theo vùng nhỏ

1. Mở https://overpass-turbo.eu trên trình duyệt điện thoại Android.
2. Kéo bản đồ đến khu vực muốn thử, nên chọn khung nhỏ khoảng 2–5 km. BBox dựa vào khung bản đồ đang mở.
3. Nhập truy vấn Overpass Turbo:

```overpassql
[out:json][timeout:90];
(
  way["highway"]["maxspeed"]({{bbox}});
  way["highway"]["maxspeed:forward"]({{bbox}});
  way["highway"]["maxspeed:backward"]({{bbox}});
);
out geom;
```

4. Nhấn Run, rồi Export → GeoJSON; lưu tệp .geojson vào Downloads. Nếu quá 8 MB, thu hẹp khung bản đồ và xuất lại.

OSM: https://wiki.openstreetmap.org/wiki/Key:maxspeed . Cấu trúc Overpass Turbo GeoJSON: https://wiki.openstreetmap.org/wiki/Overpass_turbo/GeoJSON .

## B. Nhập vào ứng dụng

1. Trên Android mở ThanhTaxiXanh → Bản đồ → Cấp quyền vị trí GPS (tùy chọn người dùng).
2. Chọn Nhập GeoJSON từ điện thoại và chọn tệp vừa xuất.
3. Chờ ứng dụng trả số đoạn đường có maxspeed; giới hạn M3 là 8 MB / 50.000 đoạn.
4. Ngắt và kết nối lại Android Auto để nạp dữ liệu mới. Nếu app không xuất hiện trên màn hình xe, kiểm tra docs/ANDROID_AUTO_PHONE_CHECKLIST.md.

## C. Hiểu kết quả

- GPS X km/h: tốc độ đo khi vị trí mới và có tốc độ GPS.
- OSM: X km/h (tham khảo): dữ liệu OSM community, KHÔNG phải biển báo hiện trường đã xác minh.
- Giới hạn: -- (chưa rõ): thiếu dữ liệu, GPS kém chính xác hoặc nhiều đường có tốc độ khác nhau cùng gần vị trí.
- M3 bỏ qua giới hạn theo điều kiện thời tiết/thời gian. Mặc định không gán một giới hạn bất kỳ khi thiếu thông tin.
- Trong khi lái xe, ưu tiên biển báo thực tế và quy định giao thông hiện hành.

## D. Bảo mật và dữ liệu

Không nhập GeoJSON từ nguồn không đáng tin. Tệp được giới hạn dung lượng, lưu riêng trong bộ nhớ ứng dụng; có nút xóa dữ liệu maxspeed.
Dữ liệu OpenStreetMap theo ODbL. Ghi nguồn © OpenStreetMap contributors và tuân thủ điều kiện dữ liệu phái sinh nếu phân phối: https://www.openstreetmap.org/copyright .
Không phải bộ dữ liệu toàn quốc; cần cơ sở dữ liệu SQLite/RTree cho giai đoạn sau.

## M4.1 — Nhập bổ sung nhiều khu vực

Trên điện thoại, sau khi nhập vùng đầu tiên, bấm **Nhập bổ sung khu vực (giữ dữ liệu cũ)** để chọn thêm GeoJSON khác. Mỗi tệp vẫn tối đa 8 MB / 50.000 đoạn, còn tổng cơ sở SQLite M4.1 có giới hạn an toàn 1.000.000 đoạn. Nhập cùng một tệp giống hệt sẽ bị bỏ qua theo SHA-256; tuy nhiên hai tệp khác nhau vẫn có thể có đoạn đường trùng nhau nếu vùng xuất chồng lấn. Chức năng **Thay toàn bộ bằng tệp GeoJSON** xóa các đoạn đường trước đó trong cùng giao dịch SQLite và chỉ ghi tệp mới nếu hợp lệ. M4.1 không tự thu thập hoặc xác thực biển báo ngoài thực tế.

## M4.2 — Khử trùng đoạn đường khi nhập nhiều vùng

- Khi nhập GeoJSON mới, ứng dụng tính định danh SHA-256 theo hai đầu đoạn đường (làm tròn tọa độ 7 chữ số thập phân), giới hạn tốc độ theo chiều, oneway và tên đường. Nếu cùng đoạn và cùng thuộc tính đã tồn tại trong SQLite, không thêm một bản sao khác. Hai tệp GeoJSON khác nội dung nhưng có đoạn trùng cũng được lọc ở cấp đoạn.
- Nếu **cùng hình học nhưng tốc độ hoặc hướng đi mâu thuẫn**, ứng dụng **giữ lại cả hai bản ghi** để ghép GPS có thể trả **Giới hạn: -- (chưa rõ)** thay vì áp đặt một tốc độ chưa kiểm chứng. Đây chưa phải hệ thống kiểm duyệt biển báo thực địa.
- Trang Bản đồ hiển thị số đoạn mới ghi và số đoạn trùng bỏ qua ở lần nhập ghi thành công gần nhất. Những tệp có trùng một phần có thể ghi ít hơn số đoạn nguồn.
- Cập nhật từ M4.1 lên M4.2 tự xây bảng định danh cho các đoạn cũ trong giao dịch nâng cấp SQLite, không xóa đường cũ. Các bản sao đã có từ trước được giữ nguyên trong lần nâng cấp; việc hợp nhất/xử lý xung đột của dữ liệu lịch sử sẽ được thực hiện ở giai đoạn tiếp theo.
