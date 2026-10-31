package vn.g98g00107.thanhtaxixanh.driver.car

import kotlin.math.*

/**
 * M5.2 conservative, pure matching engine for UNVERIFIED point candidates.
 * Output is for PHONE REVIEW ONLY; it must not drive car warnings or speed PIP.
 */
object TrafficPointRoadMatcher {
    data class Fix(val lat: Double, val lon: Double, val heading: Double?, val accuracyM: Double)
    data class Segment(val id: String, val coords: List<Pair<Double, Double>>,
                       val direction: Int = 0) // 0 unknown/two-way, +1 forward, -1 reverse
    data class Candidate(val id: String, val lat: Double, val lon: Double,
                         val wayId: String? = null, val bearing: Double? = null)
    data class Match(val pointId: String, val segmentId: String, val distanceM: Double,
                     val segmentBearing: Double, val headingDifference: Double?,
                     val confidence: String, val reason: String)

    private const val EARTH = 6371008.8
    private fun angle(a: Double, b: Double): Double = abs((a - b + 540.0) % 360.0 - 180.0)
    private fun radians(d: Double) = d * PI / 180.0
    private fun meters(lat: Double, lon: Double, originLat: Double): Pair<Double, Double> =
        Pair(EARTH * radians(lon) * cos(radians(originLat)), EARTH * radians(lat))
    private fun bearing(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val x = (b.first - a.first) * cos(radians((a.second + b.second) / 2))
        val y = b.second - a.second
        return (Math.toDegrees(atan2(x, y)) + 360.0) % 360.0
    }

    private data class Projection(val distance: Double, val heading: Double)
    private fun project(point: Candidate, s: Segment): Projection? {
        if (s.coords.size < 2) return null
        val origin = point.lat
        val p = meters(point.lat, point.lon, origin)
        var best: Projection? = null
        for (i in 0 until s.coords.size - 1) {
            val a = s.coords[i]
            val b = s.coords[i + 1]
            if (!listOf(a.first,a.second,b.first,b.second).all { it.isFinite() }) continue
            val start = meters(a.first, a.second, origin)
            val end = meters(b.first, b.second, origin)
            val dx = end.first - start.first
            val dy = end.second - start.second
            val norm = dx * dx + dy * dy
            if (norm < 0.01) continue
            val t = (((p.first - start.first) * dx + (p.second - start.second) * dy) / norm)
                .coerceIn(0.0, 1.0)
            val d = hypot(p.first - start.first - t * dx, p.second - start.second - t * dy)
            val heading = bearing(a, b)
            if (best == null || d < best.distance) best = Projection(d, heading)
        }
        return best
    }

    fun review(point: Candidate, fix: Fix?, roads: List<Segment>): Match? {
        if (!point.lat.isFinite() || !point.lon.isFinite() ||
            point.lat !in -90.0..90.0 || point.lon !in -180.0..180.0) return null
        if (fix != null && (!fix.lat.isFinite() || !fix.lon.isFinite() ||
            !fix.accuracyM.isFinite() || fix.accuracyM < 0 ||
            (fix.heading != null && (!fix.heading.isFinite() || fix.heading !in 0.0..360.0)))) return null
        val matches = roads.mapNotNull { s ->
            val p = project(point, s) ?: return@mapNotNull null
            if (p.distance > 30.0) return@mapNotNull null
            val headingDiff = fix?.heading?.let {
                if (s.direction == 0) min(angle(it, p.heading), angle(it, (p.heading + 180) % 360))
                else angle(it, if (s.direction == 1) p.heading else (p.heading + 180) % 360)
            }
            if (headingDiff != null && headingDiff > 45.0) return@mapNotNull null
            Match(point.id, s.id, p.distance, p.heading, headingDiff,
                "UNVERIFIED", "CANDIDATE_ONLY")
        }.sortedBy { it.distanceM }
        val best = matches.firstOrNull() ?: return null
        val second = matches.drop(1).firstOrNull()
        val ambiguous = second != null && second.distanceM - best.distanceM < 12.0
        val mismatchedWay = point.wayId != null && point.wayId != best.segmentId
        val inaccurate = fix == null || fix.accuracyM > 15.0 || fix.heading == null
        // Bearing from source may represent sign FACE, not vehicle heading.
        val reason = when {
            ambiguous -> "AMBIGUOUS_PARALLEL_ROADS"
            mismatchedWay -> "SOURCE_WAY_ID_MISMATCH"
            inaccurate -> "INSUFFICIENT_GPS_EVIDENCE"
            point.bearing != null -> "SIGN_BEARING_SEMANTICS_UNREVIEWED"
            else -> "NEEDS_FIELD_VERIFICATION"
        }
        return best.copy(reason = reason)
    }
}
