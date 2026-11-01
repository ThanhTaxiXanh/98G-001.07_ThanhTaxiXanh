# M5.3G — SQLite-to-directed-graph review bridge

`OsmRoadSqliteStore.topologyGraph(context, limit)` joins `road_topology` to `roads` by stored row ID and constructs directed edges with `OsmTopologyGraphBuilder`. Unlinked legacy roads are excluded. Graph size is bounded to 50,000 linked road segments; exceeding the caller's limit throws instead of returning an incomplete graph. Segment length is approximated in meters from endpoints for review only.

Tests cover legacy exclusion, a two-edge connected one-way path, and fail-closed limit behavior.

**Not implemented:** automatic SAF GeoJSON topology ingestion, OSM relation turn restrictions, HMM/Viterbi map matching, verified source/provenance auditing, in-car speed/sign warnings. No claim of accuracy for real-world roads is made.

**CI note:** an earlier Android APK build failed while fetching Kotlin Gradle artifacts because Maven returned HTTP 429, despite 44 Flutter tests passing. Do not report an APK as built until the latest Actions run succeeds.
