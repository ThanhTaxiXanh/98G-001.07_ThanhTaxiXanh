package vn.g98g00107.thanhtaxixanh.driver.car

import android.content.Context
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OsmRoadSqliteStoreTest {
    private lateinit var context: Context
    private fun road(name: String, lat: Double = 21.0, lon: Double = 106.0,
                     kmh: Int = 60) = OsmRoadSegment(
        lat, lon, lat + 0.001, lon, kmh, null, null, 0, name
    )

    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        OsmRoadSqliteStore.clear(context)
    }

    @Test fun spatialQueryFindsOnlyNearbyRoads() {
        OsmRoadSqliteStore.replace(context, listOf(
            road("A", 21.0, 106.0), road("Far", 22.0, 107.0)), 2)
        val nearby = OsmRoadSqliteStore.nearby(context, 21.0005, 106.0)
        assertNotNull(nearby)
        assertEquals(1, nearby!!.size)
        assertEquals("A", nearby[0].name)
        assertTrue(OsmRoadSqliteStore.nearby(
            context, 20.0, 105.0)!!.isEmpty())
    }

    @Test fun replacementRemovesOldSpatialRows() {
        OsmRoadSqliteStore.replace(context, listOf(road("A")), 1)
        OsmRoadSqliteStore.replace(context, listOf(road("B", 21.1)), 1)
        assertTrue(OsmRoadSqliteStore.nearby(
            context, 21.0005, 106.0)!!.isEmpty())
        assertEquals("B", OsmRoadSqliteStore.nearby(
            context, 21.1005, 106.0)!!.single().name)
    }

    @Test fun invalidReplacementPreservesOldDatabase() {
        OsmRoadSqliteStore.replace(context, listOf(road("Preserved")), 1)
        try {
            OsmRoadSqliteStore.replace(context, emptyList(), 0)
            fail("Expected invalid replacement")
        } catch (_: IllegalArgumentException) {}
        assertEquals("Preserved", OsmRoadSqliteStore.nearby(
            context, 21.0005, 106.0)!!.single().name)
        assertEquals(1, OsmRoadSqliteStore.status(context)["segments"])
    }

    @Test fun appendingDifferentRegionsPreservesBoth() {
        OsmRoadSqliteStore.replace(context, listOf(road("North")), 1,
            sourceFingerprint = "source-A")
        OsmRoadSqliteStore.replace(context, listOf(road("South", 22.0)), 1,
            append = true, sourceFingerprint = "source-B")
        assertEquals(2, OsmRoadSqliteStore.status(context)["segments"])
        assertEquals(2, OsmRoadSqliteStore.status(context)["features"])
        assertEquals("North", OsmRoadSqliteStore.nearby(
            context, 21.0005, 106.0)!!.single().name)
        assertEquals("South", OsmRoadSqliteStore.nearby(
            context, 22.0005, 106.0)!!.single().name)
    }

    @Test fun identicalFileFingerprintSkipped() {
        OsmRoadSqliteStore.replace(context, listOf(road("A")), 1,
            sourceFingerprint = "source-A")
        OsmRoadSqliteStore.replace(context, listOf(road("A")), 1,
            append = true, sourceFingerprint = "source-A")
        assertEquals(1, OsmRoadSqliteStore.status(context)["segments"])
    }

    @Test fun metadataAndClearAreConsistent() {
        val info = OsmRoadSqliteStore.replace(context, listOf(road("A")), 5)
        assertTrue(info.importedAt > 0)
        assertEquals(1, OsmRoadSqliteStore.status(context)["segments"])
        assertEquals(5, OsmRoadSqliteStore.status(context)["features"])
        OsmRoadSqliteStore.clear(context)
        assertEquals(false, OsmRoadSqliteStore.status(context)["available"])
    }
}
