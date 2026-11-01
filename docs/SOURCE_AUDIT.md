# Báo cáo nguồn nhanh — 2026-10-31

| Nguồn | Bằng chứng | Trạng thái |
|---|---|---|
| User: LunarCalendar.txt | `solarToLunar`, `lunarToSolar` và UTC+7 | READ, PORT_PENDING |
| User: Events.txt | `CalEvent`, `defaults`, `nextDate` | READ, PORT_PENDING |
| User: JavaScript tạo QR.txt | EMV AID A000000727, QRIBFTTA, CRC16 | READ, clean Dart payload implementation |
| Vela | `85eb69c946711df272b54d3f4305e72746ce1035`; `app/src/main/java/app/vela/car/VelaCarAppService.kt`, `core/src/main/java/app/vela/core/data/OsmMaxspeed.kt`, `docs/ANDROID-AUTO.md` | READ, NO_COPY (GPL notes need verify) |
| Fermata | `7d2bd42e0fea98a796ba069b56c48ef07d874549`; `FermataMediaService.java`, `CarService.java` | READ_PARTIAL, NO_COPY |
| Morphe Manager | `d9f78dfbee3f4e1db0cd30b76a57bd10dbaae3f7`; `docs/patch-sources.md` | READ_PARTIAL |
| Morphe Patches | `0bb704a9a4edb5324f6e607f3fc5a531713c607c`; `BackgroundPlaybackPatch.java` | READ_PARTIAL |
| KingInstaller | `672ff83974fab57d197d3809ea6d23f7b4afd413`; InstallationUtils/DiagnosticsUtils | READ_PARTIAL |
| HuggingFace dataset | 429 rate limit, cannot verify classes/size/license | UNVERIFIED |
| Kaggle dataset | page fetched without readable license/data | UNVERIFIED |
| OSM / OpenFreeMap | Sources referenced but tiles/offline terms not audited | UNVERIFIED |
| Thông tư 38/2024/TT-BGTVT | https://chinhphu.vn/?classid=1&docid=211873&orggroupid=4&pageid=27160 | Official 2025 effective; later amendments not conclusively ruled out |

All M0–M1 source files are original implementations; no proprietary data and no copied GPL code. Full upstream chain/commit license audit remains mandatory for future port.
