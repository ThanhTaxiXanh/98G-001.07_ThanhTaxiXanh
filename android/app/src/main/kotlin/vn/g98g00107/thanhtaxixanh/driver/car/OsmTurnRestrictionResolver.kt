package vn.g98g00107.thanhtaxixanh.driver.car

/**
 * M5.3T: map explicit OSM restriction relations onto directed topology edges.
 * Unresolved relations are counted, never silently treated as verified.
 */
object OsmTurnRestrictionResolver {
    data class Result(
        val restrictions: Set<DirectedRoadGraph.TurnRestriction>,
        val resolvedRelations: Int, val unresolvedRelations: Int,
        val status: String = "UNVERIFIED_REVIEW_ONLY"
    )

    fun resolve(
        relations: List<OsmTurnRestrictionParser.Relation>,
        edges: List<DirectedRoadGraph.Edge>
    ): Result {
        require(relations.size <= 20_000 && edges.size <= 100_000)
        val byIncoming = edges.groupBy { it.wayId to it.toNode }
        val byOutgoing = edges.groupBy { it.wayId to it.fromNode }
        val allOutgoing = edges.groupBy { it.fromNode }
        val restrictions = LinkedHashSet<DirectedRoadGraph.TurnRestriction>()
        var resolved = 0
        var unresolved = 0
        for (r in relations) {
            val incoming = byIncoming[r.fromWay to r.viaNode].orEmpty()
            val outgoing = byOutgoing[r.toWay to r.viaNode].orEmpty()
            if (incoming.isEmpty() || outgoing.isEmpty()) {
                unresolved++
                continue
            }
            // only_* requires a complete local outgoing edge set.
            // A partial topology graph cannot safely derive implicit prohibitions.
            if (r.restriction.startsWith("only_")) {
                unresolved++
                continue
            }
            for (a in incoming) for (b in outgoing) {
                restrictions.add(DirectedRoadGraph.TurnRestriction(
                    a.id,r.viaNode,b.id))
            }
            resolved++
        }
        return Result(restrictions,resolved,unresolved)
    }
}
