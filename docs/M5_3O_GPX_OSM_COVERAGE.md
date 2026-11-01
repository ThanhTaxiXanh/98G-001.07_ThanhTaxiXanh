# M5.3O — GPX to OSM offline coverage report

Android GPX SAF import now runs a read-only per-fix SQLite RTree vicinity query after GPX parsing and motion-heading estimation. The new `GpxOsmCoverageReview` reports counts of fixes with at least one road within 30 m, fixes with no road, overloaded candidate searches (more than 128 nearby segments), and geometrically ambiguous parallel roads (two nearest distances within 6 m). The phone UI displays these counters.

**Safety and limits:** This is **coverage**, not road identity, topology-aware matching, map-matching accuracy or posted speed validation. No road ID is inferred from SQLite result ordering. A nearby segment may be a wrong carriageway or grade-separated road. Heading is not used to promote matches. No GPS trace is uploaded or persisted by this report. A database without roads produces missing-road counts. No in-car alerts are activated.

Follow-up work: validate against user GPX traces and verified road geometry, add stable road IDs and directed topology to a sequence-level matcher, and expose per-point audit export.
