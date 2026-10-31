package vn.g98g00107.thanhtaxixanh.driver.car

import android.content.Context
import kotlin.math.*

/**
 * M5.3Q: conservative directed-topology audit with stable SQLite IDs.
 * Only direct consecutive segments with a shared explicit OSM node are accepted.
 * This is not HMM/Viterbi and cannot certify speed limits or trigger alerts.
 */
object GpxDirectedRoadReview {
    data class Report(val points: Int, val matched: Int, val missing: Int,
                      val ambiguous: Int, val disconnected: Int,
                      val invalidHeading: Int = 0, val implausibleTime: Int = 0,
                      val prohibitedTurn: Int = 0,
                      val status: String = "UNVERIFIED_REVIEW_ONLY")

    data class Road(val id: Long, val lat1: Double, val lon1: Double,
                    val lat2: Double, val lon2: Double)

    private fun distance(f: GpsTraceRoadReview.Fix, r: Road): Double {
        val scale = cos(Math.toRadians(f.lat))
        val ax = (r.lon1 - f.lon) * 111195.0 * scale
        val ay = (r.lat1 - f.lat) * 111195.0
        val bx = (r.lon2 - f.lon) * 111195.0 * scale
        val by = (r.lat2 - f.lat) * 111195.0
        val dx = bx - ax
        val dy = by - ay
        val len = dx * dx + dy * dy
        if (len < 0.01) return Double.POSITIVE_INFINITY
        val t = (-(ax * dx + ay * dy) / len).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy)
    }

    fun inspect(
        fixes: List<GpsTraceRoadReview.Fix>,
        graph: OsmTopologyGraphBuilder.Build,
        restrictions: Set<DirectedRoadGraph.TurnRestriction> = emptySet(),
        candidates: (GpsTraceRoadReview.Fix) -> List<Road>?
    ): Report {
        require(fixes.size <= 5000)
        val byRoad = graph.edges.groupBy { it.id.substringBefore(':') }
        var matched = 0
        var missing = 0
        var ambiguous = 0
        var disconnected = 0
        var previous: String? = null
        var previousFix: GpsTraceRoadReview.Fix? = null
        var invalidHeading = 0
        var implausibleTime = 0
        var prohibitedTurn = 0
        for (fix in fixes) {
            if (fix.heading == null || fix.accuracyM !in 0.0..25.0) {
                missing++
                previous = null
                continue
            }
            val roads = candidates(fix)
            if (roads == null) {
                ambiguous++
                previous = null
                continue
            }
            val options = roads.map { it.id.toString() to distance(fix,it) }
                .filter { it.second.isFinite() && it.second <= 30.0 }
                .sortedBy { it.second }
            if (options.isEmpty()) {
                missing++
                previous = null
                continue
            }
            if (options.size > 1 && options[1].second - options[0].second < 6.0) {
                ambiguous++
                previous = null
                continue
            }
            val roadId = options[0].first
            val road = roads.firstOrNull { it.id.toString() == roadId }
            val current = byRoad[roadId].orEmpty()
            val headingOk = road != null && current.any { edge ->
                val forward = GpxTransitionSanity.bearing(
                    road.lat1, road.lon1, road.lat2, road.lon2)
                val directedBearing = if (edge.id.endsWith(":r"))
                    (forward + 180.0) % 360.0 else forward
                GpxTransitionSanity.headingAllowed(fix.heading, directedBearing)
            }
            if (!headingOk) {
                invalidHeading++
                previous = null
                previousFix = null
                continue
            }
            if (previousFix != null &&
                !GpxTransitionSanity.travelAllowed(previousFix, fix).allowed) {
                implausibleTime++
                previous = null
                previousFix = null
                continue
            }
            if (current.isEmpty()) {
                missing++
                previous = null
                continue
            }
            if (previous != null && previous != roadId) {
                val before = byRoad[previous].orEmpty()
                val connected = before.any { a ->
                    current.any { b ->
                        GpxTurnRestrictionAudit.evaluate(a, b, restrictions)
                            .permittedByKnownRestrictions
                    }
                }
                if (!connected) {
                    val hasRestrictedTurn = before.any { a ->
                        current.any { b ->
                            GpxTurnRestrictionAudit.evaluate(a, b, restrictions)
                                .reason == "EXPLICIT_TURN_RESTRICTION"
                        }
                    }
                    if (hasRestrictedTurn) prohibitedTurn++
                    disconnected++
                    previous = null
                    continue
                }
            }
            matched++
            previous = roadId
            previousFix = fix
        }
        return Report(fixes.size,matched,missing,ambiguous,disconnected,
            invalidHeading,implausibleTime,prohibitedTurn)
    }

    fun inspectOffline(context: Context, fixes: List<GpsTraceRoadReview.Fix>): Report {
        val graph = OsmRoadSqliteStore.topologyGraph(context)
        return inspect(fixes, graph) { fix ->
            OsmRoadSqliteStore.nearbyIdentified(context,fix.lat,fix.lon)?.map {
                Road(it.roadId,it.segment.lat1,it.segment.lon1,
                    it.segment.lat2,it.segment.lon2)
            }
        }
    }
}
