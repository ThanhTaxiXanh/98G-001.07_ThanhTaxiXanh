# M5.3F — Graph assembly from explicit OSM topology

Added `OsmTopologyGraphBuilder` to create directed edges from known OSM node IDs and stored oneway direction. Bidirectional roads create two directed edges; one-way roads create one. Duplicate road IDs, malformed geometry metadata, and zero-length edges are rejected. Separate OSM nodes remain disconnected even if geometrically close. Tests cover all cases.

**Scope:** pure Kotlin builder only. The SQLite v3 topology table and atomic attach API are implemented separately, but a read-only SQLite query and importer-to-attachment pipeline are not yet wired. Current GeoJSON importer does not guarantee ordered OSM IDs; do not join records by list position. Turn restrictions are not yet imported. No Android Auto live warnings are enabled.

CI previously identified four `TrafficPointRoadMatcherTest` failures caused by reversed lat/lon axes in the bearing formula; commit `dd127b5e` corrects this. Await CI on the latest branch before reporting successful tests or APK availability.
