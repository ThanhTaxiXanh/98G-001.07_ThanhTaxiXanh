# M5.3J — Atomic strict GeoJSON-to-SQLite topology import

New `OsmRoadSqliteStore.importTopologyGeoJson(context, geoJson, source)` validates the entire input with `OsmTopologyImportPlan.prepare` before opening a write transaction. The `replace(..., topology=...)` path writes each road, fingerprint, spatial index and explicit OSM node/way link within the **same SQLite transaction**. Any invalid link, duplicate road fingerprint or insertion failure rolls back the complete replacement, preserving the previous dataset. This path deliberately does not support append, and existing non-topology import remains unchanged.

Tests include successful two-segment one-way import, duplicate rollback, and rejection of missing node IDs without destroying existing data.

**Limitations:** This is a native API, not yet wired into SAF/Flutter UI. Only LineString with explicit `osm_way_id` and ordered `osm_node_ids` is supported; ordinary OSM GeoJSON often lacks node IDs. No turn-restriction relations are imported, and speed/sign/camera warnings remain unverified and disabled. Await latest CI result; older runs have failed due to test defects and Maven HTTP 429.
