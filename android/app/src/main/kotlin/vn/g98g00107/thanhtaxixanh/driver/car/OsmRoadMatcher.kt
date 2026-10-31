package vn.g98g00107.thanhtaxixanh.driver.car

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.PI

/** Road-segment tags read from a user-imported OSM GeoJSON; never certified signs. */
data class OsmRoadSegment(
    val lat1: Double,
    val lon1: Double,
    val lat2: Double,
    val lon2: Double,
    val generalKmh: Int?,
    val forwardKmh: Int?,
    val backwardKmh: Int?,
    val oneway: Int,
    val name: String
)

data class OsmSpeedMatch(val kmh: Int, val distanceMeters: Double, val roadName: String)

/** A conservative proximity-and-heading filter; parallel roads may remain UNKNOWN. */
object OsmRoadMatcher {
    private const val DEG_TO_METERS = 111195.0

    private fun turnDelta(first: Double, second: Double): Double {
        val diff = abs((first - second + 540.0) % 360.0 - 180.0)
        return diff
    }

    /**
     * A posted OSM maxspeed is NOT independently verified by a physical sign.
     * Null means no defensible matching road, poor GPS, or competing road limits.
     */
    fun match(
        roads: List<OsmRoadSegment>,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        bearingDegrees: Double?,
        speedMetersPerSecond: Double
    ): OsmSpeedMatch? {
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0 ||
            !accuracyMeters.isFinite() || accuracyMeters !in 0.0..18.0
        ) return null
        val reliableHeading = if (bearingDegrees != null && bearingDegrees.isFinite() &&
            speedMetersPerSecond >= 3.0) {
            ((bearingDegrees % 360.0) + 360.0) % 360.0
        } else null

        val lonScale = DEG_TO_METERS * cos(latitude * PI / 180.0)
        val maxRadiusMeters = min(24.0, 10.0 + accuracyMeters)
        data class Candidate(val match: OsmSpeedMatch, val score: Double)
        val candidates = ArrayList<Candidate>()
        for (road in roads) {
            val ax = (road.lon1 - longitude) * lonScale
            val ay = (road.lat1 - latitude) * DEG_TO_METERS
            val bx = (road.lon2 - longitude) * lonScale
            val by = (road.lat2 - latitude) * DEG_TO_METERS
            val vx = bx - ax
            val vy = by - ay
            val lenSq = vx * vx + vy * vy
            if (lenSq < 1.0) continue
            val projection = (-(ax * vx + ay * vy) / lenSq).coerceIn(0.0, 1.0)
            val distance = hypot(ax + projection * vx, ay + projection * vy)
            if (distance > maxRadiusMeters) continue

            val forwardBearing = (Math.toDegrees(atan2(vx, vy)) + 360.0) % 360.0
            val forwardDelta = reliableHeading?.let { turnDelta(it, forwardBearing) }
            val reverseDelta = reliableHeading?.let { turnDelta(it, (forwardBearing + 180.0) % 360.0) }
            val direction = when {
                reliableHeading == null -> 0
                forwardDelta!! <= 55.0 -> 1
                reverseDelta!! <= 55.0 -> -1
                else -> continue
            }
            if ((road.oneway == 1 && direction == -1) ||
                (road.oneway == -1 && direction == 1)) continue

            val limit = when (direction) {
                1 -> road.forwardKmh ?: road.generalKmh
                -1 -> road.backwardKmh ?: road.generalKmh
                else -> if (road.forwardKmh != null || road.backwardKmh != null)
                    null else road.generalKmh
            } ?: continue

            val anglePenalty = when (direction) {
                1 -> forwardDelta!! / 12.0
                -1 -> reverseDelta!! / 12.0
                else -> 0.0
            }
            candidates.add(Candidate(
                OsmSpeedMatch(limit, distance, road.name),
                distance + anglePenalty
            ))
        }
        if (candidates.isEmpty()) return null
        candidates.sortBy { it.score }
        val best = candidates.first()
        // Do not pick one of two nearly equivalent lanes/roads with different limits.
        if (candidates.drop(1).any {
                it.match.kmh != best.match.kmh &&
                it.score - best.score < 6.0
            }) return null
        return best.match
    }
}
