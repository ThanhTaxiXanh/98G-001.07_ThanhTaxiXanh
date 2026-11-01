package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test

class GpsTraceRoadReviewTest {
    private val road = GpsTraceRoadReview.Road("road-east",
        21.2800, 106.2000, 21.2800, 106.2100, 1)
    private fun fix(lat: Double = 21.28001, heading: Double? = 90.0,
                    accuracy: Double = 5.0, time: Long = 1000) =
        GpsTraceRoadReview.Fix(lat, 106.2050, accuracy, heading, time)

    @Test fun emptyTrace() {
        assertEquals("NO_FIXES", GpsTraceRoadReview.review(emptyList(), listOf(road)).status)
    }

    @Test fun nearbyHeadingConsistentTraceIsCandidateOnly() {
        val result = GpsTraceRoadReview.review(listOf(fix(), fix(time = 2000)), listOf(road))
        assertEquals(listOf("road-east", "road-east"), result.roadIds)
        assertEquals("UNVERIFIED_REVIEW_ONLY", result.status)
    }

    @Test fun parallelRoadsAreNotSilentlyResolved() {
        val parallel = road.copy(id = "parallel", lat1 = 21.28004, lat2 = 21.28004)
        val result = GpsTraceRoadReview.review(listOf(fix(lat = 21.28002)),
            listOf(road, parallel))
        assertNull(result.roadIds.first())
        assertEquals(listOf(0), result.ambiguousIndices)
    }

    @Test fun wrongDirectionOnOneWayRejected() {
        assertNull(GpsTraceRoadReview.review(listOf(fix(heading = 270.0)),
            listOf(road)).roadIds.first())
    }

    @Test fun missingHeadingDoesNotProduceFalseCertainty() {
        assertNull(GpsTraceRoadReview.review(listOf(fix(heading = null)),
            listOf(road)).roadIds.first())
    }

    @Test fun poorAccuracyAndOutOfOrderTimeRejected() {
        val result = GpsTraceRoadReview.review(
            listOf(fix(), fix(accuracy = 40.0, time = 2000), fix(time = 1500)),
            listOf(road))
        assertEquals(listOf("road-east", null, null), result.roadIds)
    }
}
