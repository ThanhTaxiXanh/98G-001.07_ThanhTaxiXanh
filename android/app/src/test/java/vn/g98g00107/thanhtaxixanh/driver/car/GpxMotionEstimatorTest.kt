package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test

class GpxMotionEstimatorTest {
    private fun parsed(vararg fixes: GpsTraceRoadReview.Fix) =
        GpxTraceParser.Parsed(fixes.toList(),0)
    private fun fix(lat: Double, lon: Double, time: Long) =
        GpsTraceRoadReview.Fix(lat,lon,25.0,null,time)

    @Test fun northwardTravelProducesNorthHeading() {
        val r = GpxMotionEstimator.enrich(parsed(
            fix(21.0,106.0,1000),fix(21.001,106.0,2000)))
        assertEquals(1,r.inferredHeadings)
        assertEquals(0.0,r.fixes[1].heading!!,1.0)
    }
    @Test fun stationaryPointsRemainUncertain() {
        val r = GpxMotionEstimator.enrich(parsed(
            fix(21.0,106.0,1000),fix(21.00001,106.0,2000)))
        assertEquals(0,r.inferredHeadings)
        assertNull(r.fixes[1].heading)
    }
    @Test fun longGapDoesNotInventHeading() {
        val r = GpxMotionEstimator.enrich(parsed(
            fix(21.0,106.0,1000),fix(21.001,106.0,100_000)))
        assertNull(r.fixes[1].heading)
    }
    @Test fun eastwardTravelProducesEastHeading() {
        val r = GpxMotionEstimator.enrich(parsed(
            fix(21.0,106.0,1000),fix(21.0,106.001,2000)))
        assertEquals(90.0,r.fixes[1].heading!!,2.0)
    }
}
