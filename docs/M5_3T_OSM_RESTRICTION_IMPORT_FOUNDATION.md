# M5.3T — OSM via-node turn restriction interchange and resolver

Added bounded offline parser for a **normalized GeoJSON FeatureCollection**. Each feature must provide:
`properties.type="restriction"`, `osm_relation_id`, `from_osm_way_id`, `to_osm_way_id`, `via_osm_node_id`, `restriction` (`no_*` or `only_*`).

`OsmTurnRestrictionResolver` maps explicit `no_*` relations to `DirectedRoadGraph.TurnRestriction` by OSM way IDs and the exact via node. It counts unresolved relations and preserves provenance on parsed records. `only_*` is intentionally unresolved because a complete outgoing topology is needed to expand implicit prohibitions. This parser does **not** parse raw OSM PBF or Overpass JSON.

**Current integration boundary:** the parser and resolver are pure Kotlin with unit tests, but there is **no persisted restriction table, SAF import UI, or verified restriction dataset** in this phase. Consequently `GpxDirectedRoadReview.inspectOffline` still uses an empty restriction set and cannot claim a legal/illegal turn. Do not infer legal turns from an empty restriction list. No driver alerts are enabled. A follow-up must add transactional SQLite storage, stable snapshot mapping, explicit import and data completeness reporting before wiring into GPX review.
