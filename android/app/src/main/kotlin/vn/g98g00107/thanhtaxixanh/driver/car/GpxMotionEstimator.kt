package vn.g98g00107.thanhtaxixanh.driver.car

import kotlin.math.*

/**
 * M5.3N: derive travel bearing only when consecutive GPX points provide enough
 * movement relative to uncertainty. Inferred bearings are NOT compass readings.
 */
object GpxMotionEstimator {
    data class Result(val fixes: List<GpsTraceRoadReview.Fix>,
                      val inferredHeadings: Int, val uncertainPoints: Int)

    fun enrich(parsed: GpxTraceParser.Parsed,
               minimumMovementM: Double = 30.0,
               maximumGapMs: Long = 30_000): Result {
        require(minimumMovementM.isFinite() && minimumMovementM in 10.0..100.0)
        require(maximumGapMs in 1_000..300_000)
        val enriched = ArrayList<GpsTraceRoadReview.Fix>()
        var headings = 0
        for ((i, fix) in parsed.fixes.withIndex()) {
            val previous = parsed.fixes.getOrNull(i - 1)
            if (previous == null || fix.timeMs <= previous.timeMs ||
                fix.timeMs - previous.timeMs > maximumGapMs) {
                enriched.add(fix.copy(heading = null))
                continue
            }
            val north = (fix.lat - previous.lat) * 111_195.0
            val east = (fix.lon - previous.lon) * 111_195.0 *
                cos(Math.toRadians((fix.lat + previous.lat) / 2.0))
            val movement = hypot(north, east)
            val heading = if (movement >= minimumMovementM) {
                (Math.toDegrees(atan2(east, north)) + 360.0) % 360.0
            } else null
            if (heading != null) headings++
            enriched.add(fix.copy(heading = heading))
        }
        return Result(enriched, headings, enriched.size - headings)
    }
}
