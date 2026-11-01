package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test

class OsmRoadMatcherTest {
    private fun road(speed: Int = 60, west: Double = 106.0) =
        OsmRoadSegment(21.0, west, 21.001, west, speed, null, null, 0, "Thử nghiệm")

    @Test fun selectsNearRoad() {
        val result = OsmRoadMatcher.match(
            listOf(road()), 21.0005, 106.0, 5.0, 0.0, 12.0)
        assertEquals(60, result?.kmh)
    }

    @Test fun refusesFarAndPoorAccuracy() {
        assertNull(OsmRoadMatcher.match(listOf(road()), 21.0005, 106.02, 5.0, 0.0, 10.0))
        assertNull(OsmRoadMatcher.match(listOf(road()), 21.0005, 106.0, 40.0, 0.0, 10.0))
    }

    @Test fun refusesConflictingParallelRoadLimits() {
        val nearby = road(40, 106.000025)
        assertNull(OsmRoadMatcher.match(
            listOf(road(60), nearby), 21.0005, 106.0000125, 5.0, 0.0, 10.0))
    }

    @Test fun directionalTagRequiresHeading() {
        val directional = OsmRoadSegment(
            21.0, 106.0, 21.001, 106.0, null, 60, 40, 0, "Đường hai chiều")
        assertNull(OsmRoadMatcher.match(
            listOf(directional), 21.0005, 106.0, 5.0, null, 0.0))
        assertEquals(60, OsmRoadMatcher.match(
            listOf(directional), 21.0005, 106.0, 5.0, 0.0, 10.0)?.kmh)
        assertEquals(40, OsmRoadMatcher.match(
            listOf(directional), 21.0005, 106.0, 5.0, 180.0, 10.0)?.kmh)
    }

    @Test fun onewayExcludesWrongWay() {
        val oneWay = OsmRoadSegment(
            21.0, 106.0, 21.001, 106.0, 60, null, null, 1, "Một chiều")
        assertNull(OsmRoadMatcher.match(
            listOf(oneWay), 21.0005, 106.0, 4.0, 180.0, 10.0))
    }
}
