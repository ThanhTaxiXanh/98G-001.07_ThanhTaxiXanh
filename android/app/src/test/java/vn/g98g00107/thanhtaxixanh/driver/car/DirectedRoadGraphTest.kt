package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test

class DirectedRoadGraphTest {
    private val a = DirectedRoadGraph.Edge("a", "1", "2", 100.0, "osm:10")
    private val b = DirectedRoadGraph.Edge("b", "2", "3", 120.0, "osm:11")
    private val c = DirectedRoadGraph.Edge("c", "3", "4", 80.0, "osm:12")

    @Test fun connectedDirectedPath() {
        val result = DirectedRoadGraph.transition(listOf(a, b, c), emptySet(), "a", "c")
        assertTrue(result.allowed)
        assertEquals(300.0, result.distanceM!!, 0.001)
    }

    @Test fun cannotTravelAgainstOneWay() {
        assertFalse(DirectedRoadGraph.transition(listOf(a, b, c),
            emptySet(), "c", "a").allowed)
    }

    @Test fun turnRestrictionBlocksPath() {
        val forbidden = setOf(DirectedRoadGraph.TurnRestriction("a", "2", "b"))
        assertFalse(DirectedRoadGraph.transition(listOf(a, b, c),
            forbidden, "a", "c").allowed)
    }

    @Test fun disconnectedParallelRoadsDoNotTeleport() {
        val parallel = DirectedRoadGraph.Edge("parallel", "X", "Y", 90.0, "osm:90")
        assertFalse(DirectedRoadGraph.transition(listOf(a, b, parallel),
            emptySet(), "a", "parallel").allowed)
    }

    @Test fun distanceBoundRejectsLongDetour() {
        assertFalse(DirectedRoadGraph.transition(listOf(a, b, c),
            emptySet(), "a", "c", 250.0).allowed)
    }

    @Test fun malformedGraphIsRejected() {
        assertEquals("DUPLICATE_EDGE_ID",
            DirectedRoadGraph.transition(listOf(a, a), emptySet(), "a", "a").reason)
        assertEquals("INVALID_GRAPH",
            DirectedRoadGraph.transition(listOf(a.copy(lengthM = Double.NaN)),
                emptySet(), "a", "a").reason)
    }

    @Test fun forbiddenEdgeIsRejected() {
        assertFalse(DirectedRoadGraph.transition(listOf(a, b.copy(permitted = false)),
            emptySet(), "a", "b").allowed)
    }
}
