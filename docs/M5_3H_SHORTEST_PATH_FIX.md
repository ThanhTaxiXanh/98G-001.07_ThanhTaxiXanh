# M5.3H — Correct directed shortest-path evaluation

The M5.3B Dijkstra implementation previously returned as soon as it **discovered** the target edge, which can produce a non-shortest path. It now returns only when the target edge is **removed from the priority queue** with its best known distance. Regression test: competing routes from S to T, where a short early-discovered detour is more expensive than a later-discovered alternative; expected total 6 m.

This correction affects the offline review-only graph algorithm. It does not certify posted speed limits or enable live driver alerts.

The planned automatic topology GeoJSON-to-SQLite importer remains pending: it must pair geometry, maxspeed tags, way ID and ordered node IDs atomically, preserve deduplication semantics, and reject ambiguous or incompatible records. Existing `attachTopology` and `topologyGraph` APIs are usable for controlled tests but are not a production importer.

CI caveat: prior Android debug build failed due to Maven HTTP 429 while fetching Kotlin artifacts. A green build on the current head is still required.
