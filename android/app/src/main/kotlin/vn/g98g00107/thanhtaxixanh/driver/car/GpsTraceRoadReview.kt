package vn.g98g00107.thanhtaxixanh.driver.car

import kotlin.math.*

/**
 * M5.3 offline GPS trace-to-road geometry review prototype.
 * Not navigation, not a verified speed-limit source, and NEVER a car warning.
 * Uses a conservative temporal continuity penalty to avoid hopping parallel roads.
 */
object GpsTraceRoadReview {
    data class Fix(val lat: Double, val lon: Double, val accuracyM: Double,
                   val heading: Double? = null, val timeMs: Long)
    data class Road(val id: String, val lat1: Double, val lon1: Double,
                    val lat2: Double, val lon2: Double, val oneway: Int = 0)
    data class Result(val roadIds: List<String?>, val ambiguousIndices: List<Int>,
                      val status: String)

    private fun angle(a: Double, b: Double) = abs((a - b + 540.0) % 360.0 - 180.0)
    private fun project(f: Fix, r: Road): Pair<Double, Double>? {
        if (!listOf(r.lat1, r.lon1, r.lat2, r.lon2).all { it.isFinite() }) return null
        val k = 111195.0
        val scale = cos(Math.toRadians(f.lat))
        val ax = (r.lon1 - f.lon) * k * scale
        val ay = (r.lat1 - f.lat) * k
        val bx = (r.lon2 - f.lon) * k * scale
        val by = (r.lat2 - f.lat) * k
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 < 0.01) return null
        val t = (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val dist = hypot(ax + t * dx, ay + t * dy)
        val heading = (Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0
        return dist to heading
    }

    fun review(fixes: List<Fix>, roads: List<Road>): Result {
        require(fixes.size <= 5000 && roads.size <= 5000)
        if (fixes.isEmpty()) return Result(emptyList(), emptyList(), "NO_FIXES")
        val chosen = ArrayList<String?>()
        val ambiguous = ArrayList<Int>()
        var previous: String? = null
        for ((i, f) in fixes.withIndex()) {
            if (!f.lat.isFinite() || !f.lon.isFinite() || !f.accuracyM.isFinite() ||
                f.lat !in -90.0..90.0 || f.lon !in -180.0..180.0 ||
                f.accuracyM !in 0.0..25.0 ||
                (f.heading != null && (!f.heading.isFinite() || f.heading !in 0.0..360.0)) ||
                (i > 0 && f.timeMs <= fixes[i - 1].timeMs)) {
                chosen.add(null)
                ambiguous.add(i)
                previous = null
                continue
            }
            val options = roads.mapNotNull { r ->
                val projection = project(f, r) ?: return@mapNotNull null
                val (distance, bearing) = projection
                if (distance > 30.0) return@mapNotNull null
                val headingDifference = f.heading?.let {
                    if (r.oneway == 0) min(angle(it, bearing), angle(it, (bearing + 180) % 360))
                    else angle(it, if (r.oneway > 0) bearing else (bearing + 180) % 360)
                }
                if (headingDifference != null && headingDifference > 45.0) return@mapNotNull null
                val cost = distance + (headingDifference ?: 0.0) * 0.12 +
                    (if (previous != null && previous != r.id) 8.0 else 0.0)
                Triple(r.id, cost, distance)
            }.sortedBy { it.second }
            val best = options.firstOrNull()
            val runnerUp = options.drop(1).firstOrNull()
            val uncertain = best == null || f.heading == null ||
                (runnerUp != null && runnerUp.second - best.second < 6.0)
            if (uncertain) {
                chosen.add(null)
                ambiguous.add(i)
                previous = null
            } else {
                chosen.add(best!!.first)
                previous = best.first
            }
        }
        return Result(chosen, ambiguous, "UNVERIFIED_REVIEW_ONLY")
    }
}
