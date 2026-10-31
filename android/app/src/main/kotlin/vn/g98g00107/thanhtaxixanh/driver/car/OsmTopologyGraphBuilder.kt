package vn.g98g00107.thanhtaxixanh.driver.car

/**
 * M5.3F: assemble a directed graph only from explicitly linked OSM nodes.
 * Unlinked legacy segments are excluded. No inferred junctions or way IDs.
 */
object OsmTopologyGraphBuilder {
    data class Segment(
        val roadId: Long,
        val fromNode: String,
        val toNode: String,
        val wayId: String,
        val lengthM: Double,
        val oneway: Int
    )
    data class Build(val edges: List<DirectedRoadGraph.Edge>,
                     val rejectedSegments: Int)

    fun build(segments: List<Segment>): Build {
        require(segments.size <= 50_000)
        val edges = ArrayList<DirectedRoadGraph.Edge>()
        var rejected = 0
        val seen = HashSet<Long>()
        for (s in segments) {
            if (!seen.add(s.roadId) || s.roadId <= 0 ||
                s.fromNode.isBlank() || s.toNode.isBlank() ||
                s.fromNode == s.toNode || s.wayId.isBlank() ||
                !s.lengthM.isFinite() || s.lengthM <= 0.0 ||
                s.oneway !in -1..1) {
                rejected++
                continue
            }
            val forward = DirectedRoadGraph.Edge(
                "${s.roadId}:f", s.fromNode, s.toNode, s.lengthM, s.wayId)
            val reverse = DirectedRoadGraph.Edge(
                "${s.roadId}:r", s.toNode, s.fromNode, s.lengthM, s.wayId)
            when (s.oneway) {
                1 -> edges.add(forward)
                -1 -> edges.add(reverse)
                else -> { edges.add(forward); edges.add(reverse) }
            }
        }
        return Build(edges, rejected)
    }
}
