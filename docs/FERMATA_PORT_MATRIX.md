# Fermata port audit (2026-10-31)

Upstream https://github.com/AndreyPavlenko/Fermata @ `7d2bd42e0fea98a796ba069b56c48ef07d874549`.

| Đường nguồn đã xem | Kết luận | Destination | Trạng thái |
|---|---|---|---|
| `fermata/src/main/java/me/aap/fermata/media/service/FermataMediaService.java` | Android playback service/MediaSession; có phụ thuộc nội bộ | future Android Media3 module | AUDITED_PARTIAL / NO_COPY |
| `fermata/src/auto/java/me/aap/fermata/auto/CarService.java` | Legacy `com.google.android.apps.auto.sdk` car service | Không port SDK cũ | OMIT |

Các đường `modules/web/.../yt/` và toàn bộ chain YouTube: NOT_YET_AUDITED. Chưa port code Fermata; không công bố YouTube WebApp đang chạy. GPLv3 phải được tôn trọng nếu tái sử dụng thực.
