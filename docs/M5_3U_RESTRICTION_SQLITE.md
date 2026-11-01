# M5.3U — Atomic offline restriction snapshots

Adds `OsmRestrictionSqliteStore`, a separate version-1 SQLite database containing OSM relation ID, from/to way IDs, via node ID, restriction type and source.

- Atomic full-snapshot replacement; mixed provenance and duplicate relation IDs are rejected before starting the transaction.
- Bounded readback of at most 20,000 relations; schema migration fails rather than silently discarding data.
- Robolectric tests cover round-trip persistence, replacement and provenance mismatch.

**Scope:** persistence foundation only. The app does not yet offer a phone file-picker for importing restrictions, and the saved snapshot is not yet wired into `GpxDirectedRoadReview.inspectOffline`. A stored relation is unverified and does not authorize traffic alerts. Future integration must check that the OSM road topology snapshot and restriction snapshot are mutually consistent, resolve edges, and surface unresolved relations.
