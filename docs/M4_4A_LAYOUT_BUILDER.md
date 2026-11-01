# M4.4A — Layout Builder 12×12 trên điện thoại (VF e34 design preview)

## Phạm vi kỹ thuật

Bản này triển khai **mô hình bố cục và trình chỉnh sửa trên điện thoại Android**, không tự thay đổi host `NavigationTemplate`, `SurfaceCallback` hay `MapSnapshotter` trong Android Auto. Màn hình xe vẫn thuộc sự điều phối của Android Auto. Không coi ảnh preview là bằng chứng bố cục nhiều ô được Google duyệt.

## Cơ chế

- `lib/features/car_layout/grid12_layout.dart`: 5 panel bắt buộc ICONS/MAP/CALENDAR/QR/MEDIA. Lưới logic 12×12, icon rail cố định x=0,y=0,w=1,h=12; map tối thiểu 4×5; 3 utility tối thiểu 2×2. Validator từ chối ô thiếu, ô không hợp lệ, tọa độ ngoài lưới hoặc chồng lấn.
- `Grid12Geometry.bounds`: công thức chung chuyển ô thành tọa độ thực trong preview. Đệm 1,3% cạnh ngắn và inset 42% padding; dùng cho vẽ và nhận diện cảm ứng.
- Tám preset: `vf_e34`, `stacked`, `calendar_first`, `qr_first`, `wide_map`, `wide_tools`, `bottom_media`, `bottom_qr`.
- Mẫu `vf_e34`: icons [0,0,1,12], map [1,0,6,12], calendar [7,0,5,2], QR [7,2,5,8], media [7,10,5,2]. Đây là **đề xuất gần ảnh chụp** do người dùng cung cấp, không phải tỷ lệ thực đo qua SurfaceContainer.
- `grid12_editor.dart`: chạm và kéo để di chuyển; kéo chấm vàng góc dưới-phải để thu/phóng; chỉ chấp nhận thao tác hợp lệ. Chỉnh sửa là nháp; nhấn nút **Lưu bố cục tùy chỉnh** mới gọi LayoutStore.update.
- `car_preview.dart`: giờ vẽ đúng hình học 12×12; mã QR lấy từ payload ngân hàng đã nhập trên điện thoại, không tạo dữ liệu giả.

## Lưu cấu hình và chuyển đổi từ phiên bản trước

`layout_config.dart` dùng JSON `schemaVersion=2`, có `layoutPresetId`, `useCustomLayout`, `customLayout`. SharedPreferences vẫn sử dụng khóa `car_layout_config_v1` để không làm mất cài đặt cũ.

- Nếu mở JSON `schemaVersion=1`: chuyển tỷ lệ cột/hàng cũ sang layout custom hợp lệ gần nhất (làm tròn đến ô lưới); giữ width/height, màu, cờ GPS và các thông số khác.
- Nếu mở JSON `schemaVersion=2`: nạp preset và bố cục tùy chỉnh, kiểm tra validator trước khi dùng.
- JSON phiên bản không hỗ trợ hoặc bố cục sai bị từ chối, không âm thầm chấp nhận trạng thái hình học không hợp lệ.
- Chọn preset không xóa dữ liệu custom đã lưu; người dùng có thể khôi phục từ menu **Bố cục tùy chỉnh đã lưu**.
- Đổi độ phân giải mô phỏng không đổi tọa độ logic của các ô.

## Thao tác trên điện thoại

1. Mở ThanhTaxiXanh V5 → tab **Bố cục**.
2. Chọn preset trong danh sách tám bố cục, mặc định `VF e34 · QR lớn`.
3. Kéo ô hoặc chấm vàng để thay đổi bố cục; kiểm tra ảnh preview cập nhật.
4. Nhấn **Lưu bố cục tùy chỉnh**; có thể chọn lại preset mà không làm mất bản custom.
5. Nhấn **Hủy thay đổi** khi chưa muốn lưu; **Về mẫu VF e34** để quay về preset gần ảnh.

## Kiểm thử và giới hạn

- `test/grid12_layout_test.dart`: tám preset, hình học trên bốn tỷ lệ màn hình, không trùng/chồng lấn, JSON round-trip, chuyển tỷ lệ cũ.
- `test/grid12_config_migration_test.dart`: v1→v2, lưu/đọc lại SharedPreferences, bảo toàn custom khi đổi preset.
- `test/grid12_editor_widget_test.dart`: kéo thu ô bản đồ và render preview đa tỷ lệ.
- Chưa có đo FPS/RAM, kiểm tra DHU/VF e34, truy cập dữ liệu Media thực hoặc đưa lịch/QR lên Android Auto chính thức.

**Bước sau M4.4A:** M4.4B nghiên cứu MapLibre MapView động qua VirtualDisplay/ImageReader, thí nghiệm trên **phone preview** trước và đo hiệu năng. M4.4C kiểm tra DHU/host. M5.1 dữ liệu biển báo/camera bắt đầu khi các cổng nghiệm thu tương ứng được thống nhất.

## Tham khảo

- [Báo cáo đối chiếu AALauncherGPT](AALAUNCHERGPT_ARCHITECTURE_REVIEW.md)
- [Android Car App Library Map Surface](https://developer.android.com/training/cars/apps/library/draw-maps)
