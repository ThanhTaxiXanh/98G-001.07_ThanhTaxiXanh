package vn.g98g00107.thanhtaxixanh.driver.car

import android.content.Context

/** Phone-only M5.2B review. M4 segment IDs are internal row IDs, NOT OSM way IDs. */
object TrafficPointReviewService {
    fun inspect(context: Context, latitude: Double, longitude: Double,
                radiusDegrees: Double = 0.002): Map<String, Any> {
        require(latitude.isFinite() && longitude.isFinite() &&
            latitude in 7.0..24.5 && longitude in 101.0..116.0)
        require(radiusDegrees.isFinite() && radiusDegrees in 0.0001..0.01)
        val points = TrafficPointStore.candidates(context,
            latitude - radiusDegrees, longitude - radiusDegrees,
            latitude + radiusDegrees, longitude + radiusDegrees)
        val rows = points.take(50).map { p ->
            // The M4 store currently exposes road geometry without persistent OSM way IDs.
            // Inventing an ID mapping would incorrectly "verify" a sign.
            val roads = OsmRoadSqliteStore.nearby(context, p.latitude, p.longitude)
            val geometry = roads?.mapIndexed { i, road ->
                TrafficPointRoadMatcher.Segment("M4_ROW_UNLINKED_$i",
                    listOf(road.lat1 to road.lon1, road.lat2 to road.lon2),
                    road.oneway)
            } ?: emptyList()
            val match = TrafficPointRoadMatcher.review(
                TrafficPointRoadMatcher.Candidate(p.fingerprint, p.latitude, p.longitude,
                    p.wayId, p.bearing), null, geometry)
            mapOf(
                "id" to p.fingerprint,
                "kind" to p.kind,
                "lat" to p.latitude,
                "lon" to p.longitude,
                "speedKmh" to p.speedKmh,
                "source" to p.source,
                "license" to p.license,
                "sourceRef" to p.sourceRef,
                "verification" to "UNVERIFIED",
                "roadReview" to when {
                    roads == null -> "ROAD_QUERY_OVERFLOW_OR_UNAVAILABLE"
                    roads.isEmpty() -> "NO_ROAD_WITHIN_32M"
                    match == null -> "NO_GEOMETRIC_MATCH"
                    else -> match.reason
                },
                "nearestDistanceM" to match?.distanceM,
                "roadCount" to (roads?.size ?: -1)
            )
        }
        return mapOf(
            "points" to rows,
            "returned" to rows.size,
            "truncated" to (points.size > rows.size),
            "safety" to "PHONE_REVIEW_ONLY_NO_CAR_WARNINGS",
            "osmWayLinkAvailable" to false,
            "gpsHeadingAvailable" to false
        )
    }
}
