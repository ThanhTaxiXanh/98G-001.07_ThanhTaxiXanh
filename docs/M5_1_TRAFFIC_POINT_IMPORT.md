# M5.1 — Kho điểm biển báo/camera có tọa độ (31/10/2026)

**Phạm vi:** dữ liệu ứng viên trên điện thoại Android; chưa phải bộ cảnh báo chính xác theo đường/làn. APK không kèm danh sách biển báo/camera toàn quốc và không khai báo điểm nào là đã được kiểm chứng. Tất cả điểm nhập từ tệp đều được gán `UNVERIFIED` bất kể trường `verification` trong dữ liệu nguồn ghi gì.

## Hai cơ sở dữ liệu không được nhầm

- `osm_roads_m4.sqlite`: hình học đoạn đường OSM, `maxspeed` và ghép GPS (đã có từ M4).
- `traffic_points_m5.sqlite`: **Point** của biển báo, camera và trạm dịch vụ, có nguồn và giấy phép. Không được tự suy ra vị trí Point từ đoạn đường Way hoặc từ ảnh YOLO. Không tự cập nhật `maxspeed` theo một điểm chưa xác minh.

## Cách chuẩn bị GeoJSON

Tệp UTF-8 giới hạn **5 MB**, gồm từ 1 đến **10.000 Feature**, hệ tọa độ WGS84 và tọa độ **[kinh độ, vĩ độ]** trong khoảng bao trùm Việt Nam (kinh độ 101–116, vĩ độ 7–24,5). Mỗi `geometry` phải là `Point`, không nhận `LineString`/Polygon. `properties` có:

| Trường | Bắt buộc | Ý nghĩa |
|---|---|---|
| `kind` | Có | Loại biển/camera trong danh sách hỗ trợ |
| `source` | Có | Ví dụ `OSM`, `USER_SURVEY` hoặc tên nguồn mở |
| `license` | Có | Ví dụ `ODbL-1.0` đối với bản xuất OSM |
| `sourceRef` | Khuyên dùng | `node/123456` / URL nguồn / định danh khảo sát |
| `observedAt` | Không | Chuỗi ngày ghi nhận có thể được kiểm toán sau |
| `speedKmh` | Chỉ `speed_limit` | Số nguyên bội của 5 trong 5–130 km/h |
| `bearing` | Không | 0–359,999 độ; chưa xác minh là hướng đi hay hướng mặt biển |
| `wayId` | Không | ID đoạn đường do nguồn ghi, **chưa được ghép GPS xác nhận** |
| `verification` | Bỏ qua khi nhập | Không cho tệp tự nâng trạng thái lên `VERIFIED` |

Các `kind` hỗ trợ: `speed_limit`, `speed_limit_end`, `urban_start`, `urban_end`, `no_left_turn`, `no_right_turn`, `no_u_turn`, `no_entry`, `no_overtaking`, `no_parking`, `camera_speed`, `camera_red_light`, `camera_other`, `charging_station`, `toll_station`, `fuel_station`, `rest_area`.

Ví dụ schema (tọa độ **giả lập**, không được import để lái xe; ví dụ dưới nằm ở thư mục `test/fixtures`):

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "geometry": { "type": "Point", "coordinates": [106.2083, 21.2815] },
      "properties": {
        "kind": "speed_limit", "speedKmh": 50,
        "source": "SYNTHETIC_TEST_ONLY", "license": "SYNTHETIC_NOT_FOR_DRIVING",
        "sourceRef": "fixture/example-1"
      }
    }
  ]
}
```

## Trên điện thoại Android (không cần máy tính)

1. Chuẩn bị GeoJSON **đã có tọa độ thực tế và nguồn hợp lệ**, không phải ảnh bộ phát hiện biển báo. Có thể bắt đầu từ OSM `node[traffic_sign]` theo **một vùng nhỏ** và chuyển đổi thủ công hoặc bằng công cụ kiểm tra về schema bên trên; không tự gán các mã biển không hiểu.
2. Vào **ThanhTaxiXanh → Bản đồ → M5.1 Kho biển báo/camera có tọa độ → Nhập bổ sung GeoJSON**, chọn tệp trên điện thoại.
3. Xem số điểm mới/thêm/trùng, tổng `UNVERIFIED`. Nhập bổ sung không xóa dữ liệu trước. `Thay toàn bộ` cần xác nhận; dữ liệu mới chỉ ghi khi toàn tệp hợp lệ.
4. Nếu muốn xóa, chọn `Xóa các điểm đã nhập`. Chỉ xóa `traffic_points_m5.sqlite`, không xóa dữ liệu M4.
5. Khi có thông tin địa điểm ngoài thực tế, **chưa lái theo các marker đó**. Mốc M5.2 mới bổ sung kiểm duyệt, liên kết đoạn đường và cảnh báo nhìn về phía trước.

## Tại sao không tự hiển thị điểm lên Android Auto?

Một điểm OSM `traffic_sign` thường không đủ thông tin đường, chiều di chuyển, làn, biển phụ hay hiệu lực. Đặc biệt, biển đứng bên đường có mặt hướng ngược chiều xe tiếp cận. Nếu đưa ngay vào bản đồ cảnh báo sẽ dễ hiển thị biển trên đường song song hoặc chiều đối diện. Đối với biển tốc độ, OSM khuyên ghi `maxspeed` trên đoạn đường liên quan. Cần thẩm định theo QCVN 41:2024/BGTVT hiện hành (ban hành kèm Thông tư 51/2024/TT-BGTVT) và dữ liệu thực địa trước khi xuất kết luận.

Nguồn kỹ thuật/pháp lý:
- https://wiki.openstreetmap.org/wiki/Traffic_sign
- https://wiki.openstreetmap.org/wiki/Tag:source:maxspeed=sign
- https://wiki.openstreetmap.org/wiki/Overpass_API
- https://www.nxbgtvt.vn/giai-doan/0/0/quy-chuan-ky-thuat-quoc-gia-ve-bao-hieu-duong-bo-qcvn-41-2024-bgtvt-3751.aspx
- https://www.openstreetmap.org/copyright

## Kiểm thử và giới hạn

Unit tests: kiểm tra sai định dạng, vùng tọa độ, thiếu nguồn/giấy phép, loại và tốc độ không hợp lệ; file giả mạo `VERIFIED` vẫn lưu `UNVERIFIED`; nhập bổ sung bỏ qua điểm trùng; thay toàn bộ dùng transaction; truy vấn vùng. Flutter widget test: trạng thái và nút nhập trên điện thoại.

**Không hỗ trợ ở M5.1:** xem/duyệt danh sách điểm, sửa từng điểm, đánh dấu REVIEWED/VERIFIED, nhập Overpass raw tự động, camera thực địa, biển áp dụng theo tuyến, thông báo giọng nói hoặc phân phối bộ dữ liệu camera của bên thứ ba khi chưa rõ giấy phép. Mọi feature này cần mốc tiếp theo và test riêng.
