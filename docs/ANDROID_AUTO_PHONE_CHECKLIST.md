# Checklist trên điện thoại Android + xe

- [ ] Cài APK đúng `vn.g98g00107.thanhtaxixanh.driver`, mở được 5 tab Settings.
- [ ] Thay slider 60% -> 65%, đóng mở lại giữ cấu hình.
- [ ] Kiểm tra preview mô phỏng 934x844 không nhầm màn hình xe thật.
- [ ] Kiểm tra Android Auto version/Android ROM, quyền kết nối USB, ứng dụng có xuất hiện trong Customize launcher hay không.
- [ ] Mở Android Auto trên xe; ghi lại car service có được gọi không (Logcat tag `ThanhTaxiCar`).
- [ ] Ghi `CAR_SURFACE` kích thước / insets / visible area và ảnh từ màn hình xe.
- [ ] Không gọi FULL DASHBOARD PASS nếu chỉ có surface diagnostic.


## MapLibre OSM M2 on Android phone

1. Install the APK on Android. Open ThanhTaxiXanh > Bản đồ > Cấp quyền vị trí GPS and accept precise location (optional, explicit).
2. Turn on phone Location and mobile Internet. Connect Android Auto; try app launcher. Absence from launcher may be an Android Auto host/Play entitlement limitation, NOT evidence of successful rendering.
3. If host accepts app, log CAR_SURFACE width/height, whether OpenFreeMap Liberty style/vector tiles render, GPS/zoom, overlays in host safe area. Do not assume user-specified 934x844 is available.
4. Observe permission denial, no-network fallback, reconnect/disconnect. Verify camera/map screen in a parked vehicle, never interact with setup while driving.
