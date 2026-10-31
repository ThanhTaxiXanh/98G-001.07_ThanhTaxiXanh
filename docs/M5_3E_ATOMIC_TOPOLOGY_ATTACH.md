# M5.3E — Transactional SQLite road-topology attachment

`OsmRoadSqliteStore.attachTopology(context, entries)` takes explicit pairs of a full `OsmRoadSegment` and a validated `OsmTopologyParser.Link`. Each segment must match **exactly one** SQLite road row by ordered endpoints, maxspeed tags, one-way flag and name. All rows are inserted into `road_topology` inside one transaction; missing/ambiguous roads, duplicate target rows, invalid IDs or pre-existing topology abort the batch. No road IDs are guessed from list positions.

**Important limitation:** This is an opt-in storage API, not yet connected to GeoJSON SAF import or Android Auto. Coordinate equality is strict and may reject data that was rounded by another source; this is intentional fail-closed behavior. Existing roads without topology remain usable for non-certified OSM speed review, but cannot be promoted to verified topology. No source verification, graph assembly, GPX benchmark or warning activation is implied.

Unit tests: exact mapping, mismatch rollback, duplicate rollback. Await successful GitHub Actions for the latest commit before relying on this migration.
