package vn.g98g00107.thanhtaxixanh.driver.car

import android.content.Context
import kotlin.math.*

/**
 * M5.3O: bounded, read-only offline GPX-to-OSM geometry coverage report.
 * Road identity is NOT inferred from SQLite result ordering.
 * This never returns a verified road or speed limit.
 */
object GpxOsmCoverageReview {
    data class Report(
        val total: Int, val candidatePoints: Int, val noRoadPoints: Int,
        val overloadedPoints: Int, val ambiguousPoints: Int,
        val uncertainHeadingPoints: Int,
        val status: String = "UNVERIFIED_REVIEW_ONLY"
    )

    private fun distanceM(f: GpsTraceRoadReview.Fix, r: OsmRoadSegment): Double {
        val scale = cos(Math.toRadians(f.lat))
        val ax = (r.lon1 - f.lon) * 111195.0 * scale
        val ay = (r.lat1 - f.lat) * 111195.0
        val bx = (r.lon2 - f.lon) * 111195.0 * scale
        val by = (r.lat2 - f.lat) * 111195.0
        val dx = bx - ax
        val dy = by - ay
        val length2 = dx * dx + dy * dy
        if (length2 < 0.01) return Double.POSITIVE_INFINITY
        val t = (-(ax * dx + ay * dy) / length2).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy)
    }

    /** Inject candidate provider for deterministic unit tests. null means overflow/error. */
    fun inspect(
        fixes: List<GpsTraceRoadReview.Fix>,
        candidates: (GpsTraceRoadReview.Fix) -> List<OsmRoadSegment>?
    ): Report {
        require(fixes.size <= 5000)
        var candidatePoints = 0
        var noRoad = 0
        var overloaded = 0
        var ambiguous = 0
        var uncertain = 0
        for (fix in fixes) {
            if (fix.heading == null) uncertain++
            val nearby = candidates(fix)
            if (nearby == null) {
                overloaded++
                continue
            }
            val distances = nearby.map { distanceM(fix, it) }
                .filter { it.isFinite() && it <= 30.0 }.sorted()
            if (distances.isEmpty()) {
                noRoad++
            } else {
                candidatePoints++
                if (distances.size > 1 && distances[1] - distances[0] < 6.0) {
                    ambiguous++
                }
            }
        }
        return Report(fixes.size,candidatePoints,noRoad,overloaded,ambiguous,uncertain)
    }

    fun inspectOffline(context: Context, fixes: List<GpsTraceRoadReview.Fix>): Report =
        inspect(fixes) { fix ->
            OsmRoadSqliteStore.nearby(context, fix.lat, fix.lon)
        }
}
