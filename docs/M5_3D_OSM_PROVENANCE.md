# M5.3D — Strict OSM topology provenance parser

Added `OsmTopologyParser` and unit tests. Input is GeoJSON FeatureCollection with LineString geometry and explicit properties `osm_way_id` (positive decimal identifier) and `osm_node_ids` (ordered array matching every coordinate vertex). The parser validates longitude/latitude, non-repeated consecutive node IDs, optional layer (-10..10), bridge/tunnel flags, and records the declared source. It rejects missing or malformed topology instead of inventing OSM IDs from GeoJSON feature.id.

**Not yet integrated:** This is a standalone parser. No automatic insert into SQLite `road_topology` is performed because the current maxspeed importer filters/reorders features and does not provide a guaranteed road-row-to-topology mapping. Writing by list index could silently link the wrong road, which is unacceptable. No live speed-limit/sign warnings are enabled.

**Next:** transactional importer that binds each validated geometry segment to its newly inserted SQLite `roads.id`, with rollback on mismatch and tests for append/dedup/reimport. Follow with directed graph assembly from verified node IDs, real GPX quality metrics, and CI confirmation.
