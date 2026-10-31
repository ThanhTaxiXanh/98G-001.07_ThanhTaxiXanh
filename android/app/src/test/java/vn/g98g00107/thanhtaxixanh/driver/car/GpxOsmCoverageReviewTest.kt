package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test

class GpxOsmCoverageReviewTest {
    private val fix = GpsTraceRoadReview.Fix(21.0,106.0,25.0,null,1000L)
    private fun road(lon: Double) = OsmRoadSegment(
        20.999,lon,21.001,lon,60,null,null,0,"Test")

    @Test fun missingRoadReported() {
        val report = GpxOsmCoverageReview.inspect(listOf(fix)) { emptyList() }
        assertEquals(1,report.noRoadPoints)
        assertEquals(0,report.candidatePoints)
    }
    @Test fun closeRoadReportedAsCandidateNotVerified() {
        val report = GpxOsmCoverageReview.inspect(listOf(fix)) { listOf(road(106.0)) }
        assertEquals(1,report.candidatePoints)
        assertEquals("UNVERIFIED_REVIEW_ONLY",report.status)
        assertEquals(1,report.uncertainHeadingPoints)
    }
    @Test fun parallelRoadsFlagAmbiguity() {
        val report = GpxOsmCoverageReview.inspect(listOf(fix)) {
            listOf(road(106.0),road(106.00001))
        }
        assertEquals(1,report.ambiguousPoints)
    }
    @Test fun saturatedSearchFailsClosed() {
        val report = GpxOsmCoverageReview.inspect(listOf(fix)) { null }
        assertEquals(1,report.overloadedPoints)
        assertEquals(0,report.candidatePoints)
    }
}
