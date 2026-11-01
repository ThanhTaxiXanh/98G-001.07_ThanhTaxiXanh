# M5.3C — SQLite v3 topology sidecar

The existing `osm_roads_m4.sqlite` database now migrates from schema v1/v2 to **v3**, adding `road_topology` with columns `road_id`, `osm_way_id`, `from_osm_node`, `to_osm_node`, `layer`, `bridge`, `tunnel`, `source`. Migration preserves existing `roads`, spatial index, metadata and fingerprints; **legacy records get no fabricated topology rows**. Replacement and clear remove obsolete sidecar entries transactionally.

The table is intentionally empty until a trusted OSM source can provide real, ordered node and way identifiers. Current GeoJSON maxspeed parser does not provide them, so M5.3B directed graph remains an isolated prototype and cannot be connected to road data responsibly. No speed limits or traffic signs are verified by this migration.

Test: `OsmRoadTopologyMigrationTest` constructs a v2 SQLite file, opens it through the application store, checks schema v3, preserved legacy road and zero topology rows. CI success on the final commit is required before declaring this migration production-ready.

Next: extend the importer with explicitly validated OSM way/node references and atomic topology inserts; add graph restrictions from compatible OSM relations, then benchmark against real GPX traces.
