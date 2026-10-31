package vn.g98g00107.thanhtaxixanh.driver.car

/**
 * M5.3L: audit a sequence of GPS geometry-review matches against the explicit
 * directed OSM topology. Never promote a geometry-only match into a speed alert.
 */
object GpsTopologyContinuityAudit {
    data class Audit(
        val acceptedRoadIds: List<String?>,
        val rejectedIndices: List<Int>,
        val reasons: List<String>,
        val status: String = "UNVERIFIED_REVIEW_ONLY"
    )

    fun audit(
        trace: GpsTraceRoadReview.Result,
        graph: OsmTopologyGraphBuilder.Build,
        maxTransitionM: Double = 2000.0
    ): Audit {
        require(maxTransitionM.isFinite() && maxTransitionM in 0.0..50_000.0)
        require(trace.roadIds.size <= 5000)
        val byId = graph.edges.groupBy { it.id.substringBefore(':') }
        val accepted = ArrayList<String?>()
        val rejected = ArrayList<Int>()
        val reasons = ArrayList<String>()
        var previous: String? = null
        for ((i, roadId) in trace.roadIds.withIndex()) {
            if (roadId == null) {
                accepted.add(null)
                rejected.add(i)
                reasons.add("GPS_UNKNOWN")
                previous = null
                continue
            }
            val candidates = byId[roadId].orEmpty()
            if (candidates.isEmpty()) {
                accepted.add(null)
                rejected.add(i)
                reasons.add("ROAD_WITHOUT_OSM_TOPOLOGY")
                previous = null
                continue
            }
            if (previous != null && previous != roadId) {
                val starts = byId[previous].orEmpty()
                val possible = starts.any { a ->
                    candidates.any { b ->
                        DirectedRoadGraph.transition(
                            graph.edges, emptySet(), a.id, b.id, maxTransitionM).allowed
                    }
                }
                if (!possible) {
                    accepted.add(null)
                    rejected.add(i)
                    reasons.add("NO_DIRECTED_CONNECTION")
                    previous = null
                    continue
                }
            }
            accepted.add(roadId)
            reasons.add("REVIEW_GEOMETRY_ONLY")
            previous = roadId
        }
        return Audit(accepted, rejected, reasons)
    }
}
