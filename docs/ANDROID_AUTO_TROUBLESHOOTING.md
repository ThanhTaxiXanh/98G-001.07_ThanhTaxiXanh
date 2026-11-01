# Android Auto lỗi chưa hiển thị

Nếu APK cài nhưng không thấy trong car launcher: xác nhận gói, intent category NAVIGATION, quyền NAVIGATION_TEMPLATES, automotive_app_desc.xml, trusted distribution. Unknown Sources không áp dụng cho Car App Library app theo https://developer.android.com/training/cars/testing. Dùng Internal App Sharing/Internal Test Track chính thức để kiểm thử. Nếu host đã thấy app mà không mở: lấy log `CAR.AUTH`, `CAR.VALIDATOR`, `PlayGearheadService`, `ThanhTaxiCar`, xóa token/định vị riêng tư trước khi gửi; phân biệt vấn đề chữ ký/installer với layout limitation.
