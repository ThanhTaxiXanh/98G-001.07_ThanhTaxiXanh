package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test

class GpsSequenceMatcherTest {
    private fun c(id: String, distance: Double, connected: Boolean = true) =
        GpsSequenceMatcher.Candidate(id,distance,0.0,connected)

    @Test fun choosesUnambiguousStableId() {
        val r = GpsSequenceMatcher.match(listOf(listOf(c("osm:100:1",2.0))))
        assertEquals(listOf("osm:100:1"),r.roadIds)
        assertEquals("UNVERIFIED_REVIEW_ONLY",r.status)
    }
    @Test fun parallelCandidatesRemainUnknown() {
        val r = GpsSequenceMatcher.match(listOf(listOf(c("a",2.0),c("b",3.0))))
        assertEquals(listOf<String?>(null),r.roadIds)
        assertEquals(listOf(0),r.ambiguousIndices)
    }
    @Test fun disconnectedTransitionCannotBeAccepted() {
        val r = GpsSequenceMatcher.match(listOf(
            listOf(c("a",1.0)),listOf(c("b",1.0,false))))
        assertEquals(listOf("a",null),r.roadIds)
    }
    @Test fun emptyCandidatesFailClosed() {
        val r = GpsSequenceMatcher.match(listOf(emptyList()))
        assertEquals(listOf<String?>(null),r.roadIds)
    }
}
