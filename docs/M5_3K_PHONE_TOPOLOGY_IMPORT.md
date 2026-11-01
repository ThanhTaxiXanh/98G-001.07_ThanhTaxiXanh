# M5.3K — Android phone topology import

Phone UI now exposes **Nhập GeoJSON có OSM Node ID (thay toàn bộ)** with an explicit confirmation dialog. Android SAF document picker invokes MethodChannel `importRoadTopologyGeoJson`, reads a maximum of 8 MB off the UI thread, validates explicit `osm_way_id` and ordered `osm_node_ids`, then calls the M5.3J atomic SQLite replacement API. Cancellation preserves existing data; malformed input reports an error. This mode is separate from the original OSM maxspeed append/replace picker.

**Important:** Ordinary OSM GeoJSON may not include ordered node IDs. No OSM IDs are inferred from coordinates or `feature.id`. This is still unverified review data; in-car warnings remain disabled. The build and device behavior require GitHub Actions and real-device validation.

CI earlier exposed a null SQLite `rawQuery` bind and Robolectric JSON parser test configuration errors. Fixes were committed in `21caf077`, `e38207b1` and `a76b884e`. A green CI run on the current head is required before publishing an APK.
