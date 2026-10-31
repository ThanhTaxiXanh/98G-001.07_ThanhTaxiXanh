# M5.3Q — Topology-linked GPX review

The SQLite RTree now exposes `nearbyIdentified`: bounded candidates with **real SQLite road IDs**, joined to `road_topology` so only explicitly linked OSM nodes participate. Overflow fails closed.

`GpxDirectedRoadReview` checks each GPS point against a 30 m segment geometry threshold, rejects candidates with nearest-distance difference below 6 m, requires a heading, and checks **direct adjacency of directed OSM edges** between successive selected segments. Phone GPX review reports matched, missing, ambiguous and disconnected counts.

**Safety:** `matched` means *review candidate*, not verified identity. Current SQLite row IDs are stable within one database snapshot but can change on database replacement; do not persist them as permanent OSM identities. The algorithm does not yet check heading-versus-road bearing, elapsed-time feasibility, multi-edge routes, turn restrictions or grade-separated ambiguity. It is **not Viterbi/HMM**, does not certify maxspeed and does not activate driving warnings. GPX standard lacks measured GPS accuracy and heading; inferred heading is only movement direction. Avoid interpreting report counters as road accuracy percentages.
