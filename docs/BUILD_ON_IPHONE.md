# Build APK hoàn toàn trên iPhone

1. Trên Safari đăng nhập GitHub, mở repository `ThanhTaxiXanh/98G-001.07_ThanhTaxiXanh`.
2. Vào Pull Requests để xem nhánh `feat/smart-auto-v5-m0-m1`, hoặc mục **Actions** -> `CI • Flutter Android` -> chọn workflow run.
3. Khi build báo **Success**, mở mục **Artifacts** và tải `ThanhTaxiXanh-debug-apk`; trong ZIP có `app-debug.apk`. Nếu workflow Failed thì đọc job logs trước, không cài APK chưa tồn tại.
4. Chuyển APK qua Drive/cáp/ứng dụng chia sẻ tệp sang điện thoại Android, xác nhận cài đặt từ nguồn được cho phép.
5. Android Auto navigation templated apps có thể cần Google Play Internal App Sharing hoặc Internal Test Track để xe thật chấp nhận, xem https://developer.android.com/training/cars/testing. Sideload và bật Unknown Sources không bảo đảm.
6. Release signing đòi GitHub Secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_PASSWORD`; không đăng secrets hay keystore lên repo. Release workflow hiện giới hạn nhánh main, không tự chạy hay phát hành.
