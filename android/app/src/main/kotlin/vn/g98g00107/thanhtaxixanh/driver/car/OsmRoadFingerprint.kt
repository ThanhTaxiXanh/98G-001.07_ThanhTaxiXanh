package vn.g98g00107.thanhtaxixanh.driver.car

import java.security.MessageDigest
import kotlin.math.roundToLong

/**
 * Canonical fingerprint: same OSM geometry and tags across overlapping imports.
 * Reversed endpoints swap directional limits and oneway.
 * Conflicting tags MUST remain separate; they can lead the matcher to UNKNOWN.
 */
object OsmRoadFingerprint {
    private fun coordinate(latitude: Double, longitude: Double): String =
        "${(latitude * 10_000_000.0).roundToLong()},${(longitude * 10_000_000.0).roundToLong()}"

    fun sha256(road: OsmRoadSegment): String {
        val first = coordinate(road.lat1, road.lon1)
        val second = coordinate(road.lat2, road.lon2)
        val reverse = first > second
        val start = if (reverse) second else first
        val end = if (reverse) first else second
        val forward = if (reverse) road.backwardKmh else road.forwardKmh
        val backward = if (reverse) road.forwardKmh else road.backwardKmh
        val oneway = if (reverse) -road.oneway else road.oneway
        val name = road.name.trim()
        val content = "$start|$end|${road.generalKmh ?: "?"}|" +
            "${forward ?: "?"}|${backward ?: "?"}|$oneway|" +
            "${name.length}:$name"
        return MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
