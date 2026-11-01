# Bảng tính năng M3 (2026-10-31)

| Tính năng | Phone | Android Auto projection | AAOS |
|---|---|---|---|
| Cấu hình 5 trang | IMPLEMENTED | KHÔNG HIỂN THỊ | N/A |
| 934x844 design preview + sliders | IMPLEMENTED/TEST_PENDING | KHÔNG THAY CAR TEST | N/A |
| VietQR offline payload/CRC | IMPLEMENTED/TEST_PENDING | NOT_IMPLEMENTED | NOT_IMPLEMENTED |
| Speed rule baseline | UNIT_CODE | NOT_CONNECTED_TO_MAP | NOT_CONNECTED_TO_MAP |
| CarAppService navigation template | N/A | IMPLEMENTED/PENDING_DEVICE_TEST | NOT_TARGETED |
| Host map surface renderer | N/A | MapLibre OSM snapshot / PENDING_DEVICE_TEST | NOT_TARGETED |
| Bản đồ OSM MapLibre | NOT_IMPLEMENTED | M2 CODE / PENDING_CAR_TEST (online snapshot) | NOT_TARGETED |
| OSM routing | NOT_IMPLEMENTED | NOT_IMPLEMENTED | NOT_IMPLEMENTED |
| QR + Lịch + Media đồng thời trong car | Preview mô phỏng | BLOCKED_BY_PLATFORM | UNVERIFIED |
| Lunar calendar + event recurrence | NOT_IMPLEMENTED | NOT_IMPLEMENTED | NOT_IMPLEMENTED |
| Audio MediaLibraryService | NOT_IMPLEMENTED | NOT_IMPLEMENTED | NOT_IMPLEMENTED |
| YouTube video/WebView trong xe | KHÔNG LÀM PLAYER PHONE | BLOCKED_BY_PLATFORM (đang lái) | UNVERIFIED |
| Contribute biển/camera, SQLite | NOT_IMPLEMENTED | NOT_IMPLEMENTED | NOT_IMPLEMENTED |
| Nhập OSM GeoJSON maxspeed vùng nhỏ | M3 UI/Storage | Native matcher + PIP / PENDING_CAR_TEST | NOT_TARGETED |

| SQLite RTree spatial filtering | M4 native DB | M4 (PENDING_CAR_TEST) | NOT_TARGETED |

| Khử trùng theo đoạn + chuyển DB v1→v2 | M4.2 native SQLite | M4.2 (CI_PENDING, CAR_PENDING) | NOT_TARGETED |

## M5.1 — Dữ liệu ứng viên biển báo/camera (bổ sung 31/10/2026)

| Thành phần | Phone Android | Android Auto projection |
|---|---|---|
| Nhập GeoJSON Point (5 MB/10.000) | IMPLEMENTED / CI_PENDING | KHÔNG NHẬP TRÊN CAR |
| Kho SQLite traffic_points_m5.sqlite (≤100.000) | IMPLEMENTED / CI_PENDING | NOT_CONNECTED |
| Xác minh nguồn & giấy phép bắt buộc | FORMAT_VALIDATION | CHƯA KIỂM DUYỆT THỰC ĐỊA |
| Trùng điểm, append/replace transaction | UNIT_CODE / CI_PENDING | N/A |
| Các điểm import luôn UNVERIFIED | UNIT_CODE / CI_PENDING | KHÔNG PHÁT CẢNH BÁO |
| Converter Overpass Turbo GeoJSON (HTML local) | IMPLEMENTED / BROWSER_TEST_PENDING | N/A |
| Ghép hướng + làn + hiệu lực + camera vào tuyến | NOT_IMPLEMENTED | NOT_IMPLEMENTED |
| Android Auto VF e34 thực tế | N/A | PENDING_DEVICE_TEST |

M5.1 không nâng trạng thái CAR_2COL3ROW_REAL_SCREEN; vẫn BLOCKED_BY_PLATFORM với layout tự do.
