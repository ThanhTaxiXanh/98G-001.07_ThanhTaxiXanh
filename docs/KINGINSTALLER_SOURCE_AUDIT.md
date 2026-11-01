# KingInstaller source audit (2026-10-31)

Repository https://github.com/fcaronte/KingInstaller @ `672ff83974fab57d197d3809ea6d23f7b4afd413` branch master. Đã mở:
- `app/src/main/java/com/example/kinginstaller/InstallationUtils.kt`: APK install flow và package/source metadata.
- `app/src/main/java/com/example/kinginstaller/DiagnosticsUtils.kt`: chẩn đoán thiết bị, ROM và nguồn cài, gồm MIUI/HyperOS.
Chưa audit sâu ShizukuUtils/RootUtils/IPC; kết luận PORT = NO. Ứng dụng chính không dùng root, Shizuku, installer spoof, không tự cấp `INSTALL_PACKAGES`. Việc nhận car template phụ thuộc host/nguồn phân phối tin cậy chứ không giải quyết bằng tích hợp installer.
