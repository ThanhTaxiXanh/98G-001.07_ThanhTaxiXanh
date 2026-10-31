package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test

class GpxDirectedRoadReviewTest {
    private val graph = OsmTopologyGraphBuilder.build(listOf(
        OsmTopologyGraphBuilder.Segment(1,"a","b","100",100.0,1),
        OsmTopologyGraphBuilder.Segment(2,"b","c","101",100.0,1),
        OsmTopologyGraphBuilder.Segment(3,"z","y","102",100.0,1)
    ))
    private fun fix(lat: Double, time: Long) =
        GpsTraceRoadReview.Fix(lat,106.0,25.0,0.0,time)
    private fun road(id: Long, lat: Double) =
        GpxDirectedRoadReview.Road(id,lat - 0.001,106.0,lat + 0.001,106.0)

    @Test fun connectedRoadsAreReviewOnly() {
        val fixes = listOf(fix(21.0,1000),fix(21.01,2000))
        val result = GpxDirectedRoadReview.inspect(fixes,graph) { f ->
            if (f.lat < 21.005) listOf(road(1,21.0)) else listOf(road(2,21.01))
        }
        assertEquals(2,result.matched)
        assertEquals("UNVERIFIED_REVIEW_ONLY",result.status)
    }

    @Test fun disconnectedRoadRejected() {
        val fixes = listOf(fix(21.0,1000),fix(21.01,2000))
        val result = GpxDirectedRoadReview.inspect(fixes,graph) { f ->
            if (f.lat < 21.005) listOf(road(1,21.0)) else listOf(road(3,21.01))
        }
        assertEquals(1,result.disconnected)
    }

    @Test fun parallelCandidatesRemainAmbiguous() {
        val result = GpxDirectedRoadReview.inspect(listOf(fix(21.0,1000)),graph) {
            listOf(road(1,21.0),road(2,21.0))
        }
        assertEquals(1,result.ambiguous)
        assertEquals(0,result.matched)
    }

    @Test fun missingHeadingFailsClosed() {
        val missing = GpsTraceRoadReview.Fix(21.0,106.0,25.0,null,1000)
        val result = GpxDirectedRoadReview.inspect(listOf(missing),graph) {
            listOf(road(1,21.0))
        }
        assertEquals(1,result.missing)
    }
}
