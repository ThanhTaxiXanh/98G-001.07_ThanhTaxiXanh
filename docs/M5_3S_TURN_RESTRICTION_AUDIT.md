# M5.3S — Explicit turn restriction audit

- Added `GpxTurnRestrictionAudit` for direct directed-edge transitions, using `DirectedRoadGraph.TurnRestriction(fromEdge,viaNode,toEdge)`.
- The GPX directed reviewer accepts an optional **caller-supplied** restriction set, and rejects transitions matching explicit restrictions.
- **Critical limitation:** `inspectOffline` currently has **no imported turn restriction dataset**, so the default restriction set is empty. The phone UI therefore cannot claim that a turn is permitted or forbidden. `prohibitedTurn` is only meaningful when the caller supplies explicit restrictions. No fabricated restriction data.
- The transition checker is limited to direct adjacent edges, not via-way turn restrictions, multi-edge routing, turn-only rules, temporal restrictions, or legal signage verification. No Android Auto alerts are activated.
- Next: import validated OSM restriction relations with stable edge mappings and provenance; distinguish `UNKNOWN` from `NO_RESTRICTION` in production reports.
