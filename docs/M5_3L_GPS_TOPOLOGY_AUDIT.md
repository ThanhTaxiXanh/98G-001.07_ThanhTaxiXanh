# M5.3L — GPS trace / directed topology continuity audit

`GpsTopologyContinuityAudit.audit` consumes geometry-only `GpsTraceRoadReview.Result` and a graph built exclusively from explicit OSM node IDs. It retains road IDs only when the current road exists in topology and the previous accepted road can reach it in the directed graph within the configured bound. Unknown GPS fixes break continuity. Missing topology, disconnected roads, wrong-way transitions and overlong paths produce null results with review reasons.

This is a **prototype review layer**. It does not consume GPX directly, perform Viterbi map matching, account for elapsed GPS time, enforce turn restrictions (currently empty), or distinguish all parallel-road ambiguities. It is not wired to Android Auto and cannot authorize warnings or posted speed limits.

CI was green on commit `7bd6483b` (run 41195584836); new M5.3L changes require another green run. Tests cover connectivity, one-way direction, missing topology, uncertain GPS and path bound.
