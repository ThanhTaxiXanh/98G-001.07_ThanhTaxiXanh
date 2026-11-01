# Android Auto layout recovery — phase AA-L1

The user paused map development to prioritize visibility of the Android Auto layout.

## Compared source
`ThanhTaxiXanh/AALauncherGPT` contains a `LauncherScreen` using `NavigationTemplate`, `AppManager.setSurfaceCallback`, and `LauncherRenderer` for custom Surface drawing. Its inspected `app/src/main/AndroidManifest.xml` does **not** declare the `CarService`, so that repository alone is not proof of successful Android Auto discovery.

The current Smart Auto repository already declares `.car.SmartCarAppService` with the NAVIGATION category, and registers `CarOsmMapSurface` via `setSurfaceCallback`. Previously the Surface renderer drew a full map and a speed HUD, but **no right-hand dashboard tiles**, which explains missing tiles even when the Surface exists.

## AA-L1 implementation
- Within a host-reported safe viewport of at least 460x230 px, draw a 67% map region and 33% right-hand status column.
- Right column contains static, noninteractive calendar date, QR placeholder, and media placeholder. Do not imply functional QR/media integrations.
- If host insets are not safe or the surface is too small, preserve the existing map-only fallback.
- The Android Auto host still owns navigation chrome and can reject or constrain custom Surface content; actual VF e34 projection must be tested on device.

## Verification
Build/CI validates compilation only. In a parked car or Desktop Head Unit, verify that the app appears, a Surface is delivered, tiles render in its visible/stable rectangle, and host Back controls work. Use the existing `getCarSurfaceDiagnostics` phone method to inspect `surfaceAvailable`, viewport changes, and posted frames.

Future work: connect actual lunar calendar/events, validated QR image, and host-approved media state; avoid unsupported arbitrary Android widgets on Android Auto.
