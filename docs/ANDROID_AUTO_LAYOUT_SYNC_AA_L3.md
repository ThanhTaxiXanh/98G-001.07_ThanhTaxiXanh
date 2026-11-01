# AA-L3 — Synchronize phone Grid12 editor with Android Auto Surface

## Root cause
Flutter `LayoutStore.update()` saves `car_layout_config_v1` in Flutter SharedPreferences and sends `configChanged` over a MethodChannel. The Android Auto Surface renderer previously ignored that persisted grid and always drew a hard-coded 67/33 layout.

## Fix
- `CarGrid12Layout` reads `FlutterSharedPreferences` key `flutter.car_layout_config_v1`, the same JSON persisted by Flutter.
- Supports all eight phone presets and validates custom Grid12 tiles (five unique panels, fixed icon rail, in-bounds and non-overlapping).
- `CarOsmMapSurface` renders map and placeholder calendar/QR/media tiles using the selected tile rectangles projected into the host's reported safe Surface.
- Registers a package-local broadcast receiver while the Surface is active, reloads the saved layout on `CONFIG_CHANGED`, and immediately redraws. Receiver is unregistered on stop.
- Records `layoutUpdated` in phone-side Android Auto diagnostics.
- Does not imply that QR, calendar events, or media are integrated; the three tiles are still placeholders.

## Manual verification (parked)
1. Install the newest CI APK, open Android Auto.
2. On the phone choose `stacked` then `qr_first` in the layout tab, save each change. Confirm the calendar and QR swap positions on the car without disconnecting.
3. Edit and save a valid custom Grid12 layout, then reconnect Android Auto and verify it persists.
4. If unchanged, inspect phone diagnostics for `layoutUpdated` and `dashboardState` and verify the app/Android Auto versions match.
5. Android Auto host controls surface and template chrome; the host may clip or restrict layouts. This is not a promise of arbitrary interactive Android Auto widgets.
