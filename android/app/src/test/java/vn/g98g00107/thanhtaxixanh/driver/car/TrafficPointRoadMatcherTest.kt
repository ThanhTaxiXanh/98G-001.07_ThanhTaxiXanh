package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test

class TrafficPointRoadMatcherTest {
    private val matcher = TrafficPointRoadMatcher
    private val eastRoad = TrafficPointRoadMatcher.Segment("way-1",
        listOf(21.2800 to 106.2000, 21.2800 to 106.2100), 1)
    private fun point(lat: Double = 21.28003, lon: Double = 106.2050,
                      way: String? = null, bearing: Double? = null) =
        TrafficPointRoadMatcher.Candidate("sign-1", lat, lon, way, bearing)
    private fun fix(heading: Double? = 90.0, accuracy: Double = 5.0) =
        TrafficPointRoadMatcher.Fix(21.2800, 106.2050, heading, accuracy)

    @Test fun acceptsNearbyCandidateButNeverVerifies() {
        val match = matcher.review(point(), fix(), listOf(eastRoad))!!
        assertEquals("way-1", match.segmentId)
        assertEquals("UNVERIFIED", match.confidence)
        assertEquals("NEEDS_FIELD_VERIFICATION", match.reason)
        assertTrue(match.distanceM < 5.0)
    }

    @Test fun parallelRoadsAreAmbiguousEvenWithGoodGps() {
        val parallel = TrafficPointRoadMatcher.Segment("way-2",
            listOf(21.28007 to 106.2000, 21.28007 to 106.2100), 1)
        val match = matcher.review(point(), fix(), listOf(eastRoad, parallel))!!
        assertEquals("AMBIGUOUS_PARALLEL_ROADS", match.reason)
    }

    @Test fun wrongWayOnOneWayRoadIsRejected() {
        assertNull(matcher.review(point(), fix(270.0), listOf(eastRoad)))
    }

    @Test fun sourceWayMismatchIsNotPromoted() {
        val match = matcher.review(point(way = "way-999"), fix(), listOf(eastRoad))!!
        assertEquals("SOURCE_WAY_ID_MISMATCH", match.reason)
    }

    @Test fun signFaceBearingIsNotAssumedToBeTravelDirection() {
        val match = matcher.review(point(bearing = 270.0), fix(), listOf(eastRoad))!!
        assertEquals("SIGN_BEARING_SEMANTICS_UNREVIEWED", match.reason)
    }

    @Test fun missingOrLowQualityGpsIsUnverified() {
        assertEquals("INSUFFICIENT_GPS_EVIDENCE",
            matcher.review(point(), fix(null, 25.0), listOf(eastRoad))!!.reason)
        assertEquals("INSUFFICIENT_GPS_EVIDENCE",
            matcher.review(point(), null, listOf(eastRoad))!!.reason)
    }

    @Test fun farAwayPointDoesNotMatch() {
        assertNull(matcher.review(point(21.2850), fix(), listOf(eastRoad)))
    }

    @Test fun invalidCoordinatesAreRejected() {
        assertNull(matcher.review(point(Double.NaN), fix(), listOf(eastRoad)))
        assertNull(matcher.review(point(), fix(accuracy = Double.NaN), listOf(eastRoad)))
    }
}
