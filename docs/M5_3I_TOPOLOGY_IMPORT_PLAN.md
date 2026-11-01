# M5.3I — Safe per-feature GeoJSON topology import plan

`OsmTopologyImportPlan.prepare(geoJson, source)` parses each GeoJSON feature independently with the existing maxspeed catalog and strict OSM topology parser. It returns exact ordered pairs of `OsmRoadSegment` and `OsmTopologyParser.Link`. Features without supported maxspeed are skipped. Any speed feature with missing or incompatible topology rejects the whole plan before a database write. The 8 MB/50,000-segment bounds are retained.

This eliminates the unsafe approach of zipping independently filtered road and topology arrays. It does **not** yet implement an atomic combined road+topology SQLite importer, deduplication reconciliation, or Android SAF UI wiring. The existing `attachTopology` API requires roads already present; using `replace` and `attachTopology` sequentially would NOT be atomic across both calls. Do not claim otherwise.

Unit tests cover exact node/segment alignment, skipped non-speed features, missing IDs, and unsupported MultiLineString. CI must confirm the changes before an APK is reported as successful.
