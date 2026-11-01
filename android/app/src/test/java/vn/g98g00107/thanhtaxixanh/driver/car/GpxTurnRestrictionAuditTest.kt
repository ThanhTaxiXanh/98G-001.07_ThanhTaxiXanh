package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test

class GpxTurnRestrictionAuditTest {
    private val a = DirectedRoadGraph.Edge("1:f","A","B",50.0,"way1")
    private val b = DirectedRoadGraph.Edge("2:f","B","C",60.0,"way2")
    private val disconnected = DirectedRoadGraph.Edge("3:f","X","Y",50.0,"way3")

    @Test fun explicitProhibitionIsRejected() {
        val restrictions = setOf(DirectedRoadGraph.TurnRestriction("1:f","B","2:f"))
        val result = GpxTurnRestrictionAudit.evaluate(a,b,restrictions)
        assertFalse(result.permittedByKnownRestrictions)
        assertEquals("EXPLICIT_TURN_RESTRICTION",result.reason)
    }
    @Test fun noKnownRestrictionIsNotProofOfLegalTurn() {
        val result = GpxTurnRestrictionAudit.evaluate(a,b,emptySet())
        assertTrue(result.permittedByKnownRestrictions)
        assertEquals("NO_MATCHING_KNOWN_RESTRICTION",result.reason)
    }
    @Test fun disconnectedEdgesRejected() {
        val result = GpxTurnRestrictionAudit.evaluate(a,disconnected,emptySet())
        assertFalse(result.permittedByKnownRestrictions)
    }
    @Test fun nonPermittedEdgeRejected() {
        val blocked = b.copy(permitted = false)
        assertFalse(GpxTurnRestrictionAudit.evaluate(a,blocked,emptySet())
            .permittedByKnownRestrictions)
    }
}
