package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test

class OsmTopologyGraphBuilderTest {
    private fun segment(direction: Int = 0) = OsmTopologyGraphBuilder.Segment(
        7, "101", "102", "99", 100.0, direction)

    @Test fun twoWayHasBothDirections() {
        val result = OsmTopologyGraphBuilder.build(listOf(segment()))
        assertEquals(2, result.edges.size)
        assertEquals(setOf("101" to "102", "102" to "101"),
            result.edges.map { it.fromNode to it.toNode }.toSet())
    }
    @Test fun oneWayForwardOnly() {
        val result = OsmTopologyGraphBuilder.build(listOf(segment(1)))
        assertEquals(1, result.edges.size)
        assertEquals("101", result.edges.single().fromNode)
    }
    @Test fun reversedOneWayOnly() {
        val result = OsmTopologyGraphBuilder.build(listOf(segment(-1)))
        assertEquals(1, result.edges.size)
        assertEquals("102", result.edges.single().fromNode)
    }
    @Test fun invalidAndDuplicateSegmentsRejected() {
        val result = OsmTopologyGraphBuilder.build(listOf(segment(),
            segment(), segment().copy(roadId=8,fromNode="101",toNode="101")))
        assertEquals(2, result.edges.size)
        assertEquals(2, result.rejectedSegments)
    }
    @Test fun separateNodesRemainDisconnected() {
        val edges = OsmTopologyGraphBuilder.build(listOf(segment(1),
            segment(1).copy(roadId=8,fromNode="201",toNode="202"))).edges
        assertFalse(DirectedRoadGraph.transition(edges,emptySet(),"7:f","8:f").allowed)
    }
}
