# AA-L4 — Editable grid boundaries, map aspect ratio and Vietnamese lunar date

## Source comparison
Reviewed `ThanhTaxiXanh/AALauncherGPT`:
- `DashboardLayout.kt` validates a bounded 12×12 tile tree with no overlaps.
- `DashboardLayoutPreview.kt` supports drag-to-move and bottom-right resize on the **phone**.
- `LauncherRenderer.kt` renders a dashboard with map, calendar, QR and signs in its `render()` method, but its separate `renderNavigationSurface()` explicitly draws **map only** on Android Auto. Do not claim that AALauncherGPT demonstrates unrestricted dashboard widgets on the Android Auto host.
- `LunarCalendar.kt` provides Hồ Ngọc Đức-style solar-to-lunar conversion and Can Chi year.

## Implementation
- Phone `Grid12Layout.shiftBoundary` adjusts two aligned, adjacent tiles simultaneously; invalid or undersized results are rejected without changing the saved grid. The editor exposes +/− controls for available shared row and column boundaries, alongside existing drag/resize.
- The car renderer uses the same persisted Grid12 layout and redraws on phone configuration broadcasts (AA-L3).
- The map snapshot is center-cropped to preserve aspect ratio in a resized map tile; GPS and attribution overlays are clipped to that tile.
- Calendar tile shows today's Vietnam-local Gregorian date, lunar day/month, leap-month marker and Can Chi lunar year using the reference algorithm.
- QR and media remain placeholders. User events are not yet integrated into the car tile.

## Limitations
In a tightly packed preset, not every tile can be dragged or resized independently. Shared-boundary controls allow practical adjustment where panels have matching adjacent edges; arbitrary reflow, split/merge and multi-panel resizing remain future work. Android Auto's NavigationTemplate host can constrain custom content; phone preview is not proof of host behavior.

## Verification
CI must pass before APK installation. On a parked vehicle, select `stacked`, choose Calendar, adjust the shared row boundary with QR, save and verify that the car redraws. Verify the map does not distort, and that lunar dates agree with the phone on a known test date.
