package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test

class GpsTopologyContinuityAuditTest {
    private val graph = OsmTopologyGraphBuilder.build(listOf(
        OsmTopologyGraphBuilder.Segment(1,"10","11","100",20.0,1),
        OsmTopologyGraphBuilder.Segment(2,"11","12","101",20.0,1),
        OsmTopologyGraphBuilder.Segment(3,"90","91","102",20.0,1)
    ))
    private fun trace(vararg ids: String?) =
        GpsTraceRoadReview.Result(ids.toList(), emptyList(), "UNVERIFIED_REVIEW_ONLY")

    @Test fun connectedOneWaySegmentsRemainReviewOnly() {
        val audit = GpsTopologyContinuityAudit.audit(trace("1","2"),graph)
        assertEquals(listOf("1","2"),audit.acceptedRoadIds)
        assertEquals("UNVERIFIED_REVIEW_ONLY",audit.status)
    }

    @Test fun disconnectedParallelRoadRejected() {
        val audit = GpsTopologyContinuityAudit.audit(trace("1","3"),graph)
        assertEquals(listOf("1",null),audit.acceptedRoadIds)
        assertEquals("NO_DIRECTED_CONNECTION",audit.reasons[1])
    }

    @Test fun reverseOneWayTransitionRejected() {
        val audit = GpsTopologyContinuityAudit.audit(trace("2","1"),graph)
        assertEquals("NO_DIRECTED_CONNECTION",audit.reasons[1])
    }

    @Test fun missingTopologyCannotBecomeVerified() {
        val audit = GpsTopologyContinuityAudit.audit(trace("1","999"),graph)
        assertEquals("ROAD_WITHOUT_OSM_TOPOLOGY",audit.reasons[1])
    }

    @Test fun unknownGpsBreaksContinuity() {
        val audit = GpsTopologyContinuityAudit.audit(trace("1",null,"3"),graph)
        assertEquals(listOf("1",null,"3"),audit.acceptedRoadIds)
        assertEquals("GPS_UNKNOWN",audit.reasons[1])
    }

    @Test fun boundPreventsUnreasonablyLongTransition() {
        val audit = GpsTopologyContinuityAudit.audit(trace("1","2"),graph,30.0)
        assertEquals("NO_DIRECTED_CONNECTION",audit.reasons[1])
    }
}
