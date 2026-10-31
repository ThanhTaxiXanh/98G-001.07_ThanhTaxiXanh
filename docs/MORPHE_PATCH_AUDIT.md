# Morphe audit (2026-10-31)

- Manager https://github.com/MorpheApp/morphe-manager @ `d9f78dfbee3f4e1db0cd30b76a57bd10dbaae3f7`; `docs/patch-sources.md`: quản lý nguồn bundle patch, KHÔNG là engine Flutter YouTube.
- Patches https://github.com/MorpheApp/morphe-patches @ `0bb704a9a4edb5324f6e607f3fc5a531713c607c`; `extensions/youtube/src/main/java/app/morphe/extension/youtube/patches/BackgroundPlaybackPatch.java`: mã extension YouTube và nguồn ReVanced được nêu trong phần đầu file. Không thể áp dụng nguyên bytecode patch vào WebView.
- Khả năng chuyển sang Flutter: `NOT_APPLICABLE` cho bytecode patch; `REIMPLEMENT` chỉ cho functionality có API Android công khai; test chưa có. Không sao chép, không dùng nhãn/logo Morphe, không bypass vehicle safety.
