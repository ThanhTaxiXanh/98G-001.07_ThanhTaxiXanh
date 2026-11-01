package vn.g98g00107.thanhtaxixanh.driver.car

/**
 * M5.3S: conservative direct-edge turn restriction audit.
 * Restrictions must be supplied with explicit directed edge IDs and via OSM nodes.
 * Absence of restrictions is UNKNOWN, never proof that a turn is legal.
 */
object GpxTurnRestrictionAudit {
    data class Verdict(val permittedByKnownRestrictions: Boolean, val reason: String)

    fun evaluate(
        previous: DirectedRoadGraph.Edge,
        next: DirectedRoadGraph.Edge,
        restrictions: Set<DirectedRoadGraph.TurnRestriction>
    ): Verdict {
        if (!previous.permitted || !next.permitted)
            return Verdict(false, "EDGE_NOT_PERMITTED")
        if (previous.toNode != next.fromNode)
            return Verdict(false, "DISCONNECTED_DIRECTED_EDGES")
        if (DirectedRoadGraph.TurnRestriction(
                previous.id, previous.toNode, next.id) in restrictions)
            return Verdict(false, "EXPLICIT_TURN_RESTRICTION")
        return Verdict(true, "NO_MATCHING_KNOWN_RESTRICTION")
    }
}
