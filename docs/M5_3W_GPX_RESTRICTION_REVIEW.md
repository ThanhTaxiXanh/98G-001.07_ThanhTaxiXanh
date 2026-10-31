# M5.3W — Offline GPX review with imported OSM restrictions

`GpxDirectedRoadReview.inspectOffline` now reads the atomic SQLite restriction snapshot, resolves explicit OSM `no_*` via-node relations against the current directed road topology and passes mapped prohibitions into the existing conservative GPX audit.

The Android method channel and Flutter phone report expose:
- total imported restriction relations
- relations mapped onto current topology
- unresolved relations (including all `only_*` rules)
- observed GPX transitions conflicting with explicitly mapped prohibitions

**Critical limitations:** topology and restriction datasets are not yet proven to share the same OSM extract/version; a match is not legal proof. The directed GPX review remains greedy and cannot certify a real-world turn. Missing or unresolved relations must not be interpreted as legal turns. This report does not drive routing, vehicle-speed limits, Android Auto alerts, or safety-critical behavior. Status is `UNVERIFIED_REVIEW_ONLY`.
