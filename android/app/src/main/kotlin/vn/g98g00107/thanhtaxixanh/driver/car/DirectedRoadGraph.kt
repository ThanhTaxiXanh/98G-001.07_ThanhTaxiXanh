package vn.g98g00107.thanhtaxixanh.driver.car

import kotlin.math.*

/**
 * M5.3B: conservative, offline, directed graph transition evaluator.
 * This does NOT authorize a speed-limit/sign warning.
 * Node identifiers must come from a stable source such as OSM node IDs.
 */
object DirectedRoadGraph {
    data class Edge(
        val id: String,
        val fromNode: String,
        val toNode: String,
        val lengthM: Double,
        val wayId: String?,
        val permitted: Boolean = true
    )
    data class TurnRestriction(val fromEdge: String, val viaNode: String,
                               val toEdge: String)
    data class Transition(val allowed: Boolean, val distanceM: Double?,
                          val reason: String)

    /** Dijkstra over directed edges. No teleporting between disconnected roads. */
    fun transition(
        edges: List<Edge>, restrictions: Set<TurnRestriction>,
        startEdge: String, endEdge: String,
        maxDistanceM: Double = 2000.0
    ): Transition {
        require(edges.size <= 50_000)
        require(maxDistanceM.isFinite() && maxDistanceM in 0.0..50_000.0)
        if (edges.any { it.lengthM <= 0.0 || !it.lengthM.isFinite() ||
                it.fromNode.isBlank() || it.toNode.isBlank() || it.id.isBlank() }) {
            return Transition(false, null, "INVALID_GRAPH")
        }
        val byId = edges.associateBy { it.id }
        if (byId.size != edges.size) return Transition(false, null, "DUPLICATE_EDGE_ID")
        val start = byId[startEdge] ?: return Transition(false, null, "START_NOT_FOUND")
        val target = byId[endEdge] ?: return Transition(false, null, "TARGET_NOT_FOUND")
        if (!start.permitted || !target.permitted)
            return Transition(false, null, "EDGE_NOT_PERMITTED")
        if (startEdge == endEdge) return Transition(true, 0.0, "SAME_EDGE")
        val outgoing = edges.filter { it.permitted }.groupBy { it.fromNode }
        val queue = java.util.PriorityQueue(compareBy<Pair<Double, String>> { it.first })
        val best = HashMap<String, Double>()
        queue.add(start.lengthM to start.id)
        best[start.id] = start.lengthM
        while (queue.isNotEmpty()) {
            val (distance, edgeId) = queue.poll()
            if (distance > maxDistanceM) break
            if (distance > (best[edgeId] ?: Double.POSITIVE_INFINITY)) continue
            if (edgeId == endEdge)
                return Transition(true, distance, "CONNECTED_DIRECTED_PATH")
            val edge = byId[edgeId] ?: continue
            for (next in outgoing[edge.toNode].orEmpty()) {
                if (TurnRestriction(edgeId, edge.toNode, next.id) in restrictions) continue
                val nextDistance = distance + next.lengthM
                if (nextDistance < (best[next.id] ?: Double.POSITIVE_INFINITY) &&
                    nextDistance <= maxDistanceM) {
                    best[next.id] = nextDistance
                    queue.add(nextDistance to next.id)
                }
            }
        }
        return Transition(false, null, "NO_PERMITTED_PATH_WITHIN_LIMIT")
    }
}
