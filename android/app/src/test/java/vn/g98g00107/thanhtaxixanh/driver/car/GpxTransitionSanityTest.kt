package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test

class GpxTransitionSanityTest {
    private fun fix(lat: Double, time: Long) =
        GpsTraceRoadReview.Fix(lat,106.0,25.0,0.0,time)

    @Test fun northHeadingMatchesNorthRoad() {
        val bearing = GpxTransitionSanity.bearing(21.0,106.0,21.001,106.0)
        assertTrue(GpxTransitionSanity.headingAllowed(0.0,bearing))
        assertFalse(GpxTransitionSanity.headingAllowed(180.0,bearing))
    }
    @Test fun reverseAndMissingHeadingFailClosed() {
        assertFalse(GpxTransitionSanity.headingAllowed(null,0.0))
        assertFalse(GpxTransitionSanity.headingAllowed(Double.NaN,0.0))
        assertTrue(GpxTransitionSanity.headingAllowed(359.0,1.0))
    }
    @Test fun impossibleGpsJumpRejected() {
        val result = GpxTransitionSanity.travelAllowed(
            fix(21.0,1000),fix(21.01,2000))
        assertFalse(result.allowed)
        assertEquals("IMPLAUSIBLE_DISPLACEMENT",result.reason)
    }
    @Test fun plausibleGpsTravelAccepted() {
        assertTrue(GpxTransitionSanity.travelAllowed(
            fix(21.0,1000),fix(21.001,11000)).allowed)
    }
    @Test fun staleOrNonmonotonicTimestampsRejected() {
        assertFalse(GpxTransitionSanity.travelAllowed(
            fix(21.0,1000),fix(21.001,1000)).allowed)
        assertFalse(GpxTransitionSanity.travelAllowed(
            fix(21.0,1000),fix(21.001,60000)).allowed)
    }
}
