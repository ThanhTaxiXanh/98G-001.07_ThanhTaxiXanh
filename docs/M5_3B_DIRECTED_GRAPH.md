# M5.3B — Directed road graph transition review

Prototype 31/10/2026: `DirectedRoadGraph` checks whether a directed path can connect two edge IDs. Uses stable node IDs, direction-specific edges, explicit forbidden edge-to-edge turns, bounded distance and Dijkstra-style priority queue. Unit tests cover connected routes, reverse one-way, forbidden turn, disconnected parallel roads, distance bound, duplicate/invalid graph and forbidden edges.

**No false source claims:** current M4 SQLite import does not preserve OSM node IDs or OSM way IDs. The new graph is an isolated algorithm, NOT wired to M4, M5.2B, live GPS or Android Auto. No turn restriction dataset is bundled. Neither this graph nor the greedy M5.3 trace matcher certifies a speed limit or a traffic sign.

**Distance convention:** transition distance currently includes full length of start edge and all traversed edges; same-edge transitions return 0. This is a conservative connectivity heuristic, not a precise GPS-to-GPS travel distance. No partial-edge offset, u-turn model, vehicle profile, turn-only restrictions, route-level Viterbi/HMM, elevation/layer model or quality metrics yet. It can reject legitimate transitions when the distance budget is too low.

Next: extend the OSM import schema transactionally with durable way ID, ordered OSM node IDs, layer/bridge/tunnel, and turn restrictions, retaining compatibility with existing SQLite data. Build a sequence-level candidate/transition scorer with explicit UNKNOWN, validate against real GPX and report false-match rate before any in-car warning.
