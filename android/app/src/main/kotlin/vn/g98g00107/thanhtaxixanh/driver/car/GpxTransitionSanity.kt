package vn.g98g00107.thanhtaxixanh.driver.car

import kotlin.math.*

/** M5.3R: fail-closed directional and temporal checks for offline GPS review. */
object GpxTransitionSanity {
    data class Verdict(val allowed: Boolean, val reason: String)
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val north = (lat2 - lat1) * 111195.0
        val east = (lon2 - lon1) * 111195.0 *
            cos(Math.toRadians((lat1 + lat2) / 2.0))
        if (hypot(north, east) < 0.5) return Double.NaN
        return (Math.toDegrees(atan2(east, north)) + 360.0) % 360.0
    }
    fun headingAllowed(observed: Double?, roadBearing: Double,
                       toleranceDeg: Double = 45.0): Boolean {
        if (observed == null || !observed.isFinite() ||
            !roadBearing.isFinite() || observed !in 0.0..360.0 ||
            toleranceDeg !in 0.0..90.0) return false
        val difference = abs((observed - roadBearing + 540.0) % 360.0 - 180.0)
        return difference <= toleranceDeg
    }
    fun travelAllowed(previous: GpsTraceRoadReview.Fix,
                      current: GpsTraceRoadReview.Fix,
                      maxMetersPerSecond: Double = 55.0,
                      maxGapMs: Long = 30_000): Verdict {
        val deltaMs = current.timeMs - previous.timeMs
        if (deltaMs <= 0L || deltaMs > maxGapMs)
            return Verdict(false, "INVALID_TIME_GAP")
        val north = (current.lat - previous.lat) * 111195.0
        val east = (current.lon - previous.lon) * 111195.0 *
            cos(Math.toRadians((current.lat + previous.lat) / 2.0))
        val speed = hypot(north, east) * 1000.0 / deltaMs
        if (!speed.isFinite() || speed > maxMetersPerSecond)
            return Verdict(false, "IMPLAUSIBLE_DISPLACEMENT")
        return Verdict(true, "PLAUSIBLE_GPS_DISPLACEMENT")
    }
}
