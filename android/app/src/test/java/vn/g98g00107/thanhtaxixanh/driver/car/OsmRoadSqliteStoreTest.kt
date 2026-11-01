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

    @Test fun overlappingRegionalImportSkipsExactSegments() {
        OsmRoadSqliteStore.replace(context,
            listOf(road("Shared"), road("Old", 21.1)), 2,
            sourceFingerprint = "region-old")
        val after = OsmRoadSqliteStore.replace(context,
            listOf(road("Shared"), road("New", 22.0)), 2,
            append = true, sourceFingerprint = "region-new")
        assertEquals(3, after.segments)
        assertEquals(3, OsmRoadSqliteStore.status(context)["segments"])
        assertEquals(1, OsmRoadSqliteStore.status(context)["lastAdded"])
        assertEquals(1, OsmRoadSqliteStore.status(context)["lastSkippedDuplicates"])
        assertEquals("Old", OsmRoadSqliteStore.nearby(
            context, 21.1005, 106.0)!!.single().name)
        assertEquals("New", OsmRoadSqliteStore.nearby(
            context, 22.0005, 106.0)!!.single().name)
    }

    @Test fun sameSegmentAppearingTwiceInSingleFileIsDeduped() {
        OsmRoadSqliteStore.replace(context, listOf(road("A"), road("A")), 2)
        assertEquals(1, OsmRoadSqliteStore.status(context)["segments"])
        assertEquals(1, OsmRoadSqliteStore.status(context)["lastSkippedDuplicates"])
    }

    @Test fun conflictingSpeedForSameGeometryIsKeptAndMatcherReturnsUnknown() {
        OsmRoadSqliteStore.replace(context, listOf(road("A", kmh = 60)), 1,
            sourceFingerprint = "first")
        OsmRoadSqliteStore.replace(context, listOf(road("A", kmh = 40)), 1,
            append = true, sourceFingerprint = "second")
        val near = OsmRoadSqliteStore.nearby(context, 21.0005, 106.0)!!
        assertEquals(2, near.size)
        assertNull(OsmRoadMatcher.match(near,
            21.0005, 106.0, 5.0, 0.0, 10.0))
    }

    @Test fun reversingEndpointsWithEquivalentTagsDoesNotDuplicate() {
        val forward = OsmRoadSegment(
            21.0, 106.0, 21.001, 106.0, 60, 70, 40, 1, "One-way")
        val reverse = OsmRoadSegment(
            21.001, 106.0, 21.0, 106.0, 60, 40, 70, -1, "One-way")
        OsmRoadSqliteStore.replace(context, listOf(forward), 1)
        OsmRoadSqliteStore.replace(context, listOf(reverse), 1, append = true)
        assertEquals(1, OsmRoadSqliteStore.status(context)["segments"])
    }

    @Test fun upgradesOldM4DatabaseWithoutDroppingItsRoads() {
        context.deleteDatabase("osm_roads_m4.sqlite")
        val legacy = context.openOrCreateDatabase(
            "osm_roads_m4.sqlite", Context.MODE_PRIVATE, null)
        try {
            legacy.execSQL("""
                CREATE TABLE roads (
                    id INTEGER PRIMARY KEY, lat1 REAL NOT NULL,
                    lon1 REAL NOT NULL, lat2 REAL NOT NULL, lon2 REAL NOT NULL,
                    base_kmh INTEGER, forward_kmh INTEGER,
                    backward_kmh INTEGER, oneway INTEGER NOT NULL,
                    name TEXT NOT NULL)
            """.trimIndent())
            legacy.execSQL("""
                CREATE TABLE road_rtree (
                    id INTEGER PRIMARY KEY, min_lat REAL, max_lat REAL,
                    min_lon REAL, max_lon REAL)
            """.trimIndent())
            legacy.execSQL("CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
            legacy.execSQL("""
                INSERT INTO roads(id,lat1,lon1,lat2,lon2,base_kmh,oneway,name)
                VALUES(1,21.0,106.0,21.001,106.0,60,0,'Legacy')
            """.trimIndent())
            legacy.execSQL("""
                INSERT INTO road_rtree(id,min_lat,max_lat,min_lon,max_lon)
                VALUES(1,21.0,21.001,106.0,106.0)
            """.trimIndent())
            legacy.execSQL("INSERT INTO metadata(key,value) VALUES('indexType','BTreeBBox')")
            legacy.version = 1
        } finally {
            legacy.close()
        }
        assertEquals(1, OsmRoadSqliteStore.status(context)["segments"])
        OsmRoadSqliteStore.replace(context, listOf(road("Legacy")), 1,
            append = true, sourceFingerprint = "new-file")
        assertEquals(1, OsmRoadSqliteStore.status(context)["segments"])
    }


    private fun injectLegacyCopy(speed: Int = 60, name: String = "Duplicate") {
        val db = context.openOrCreateDatabase(
            "osm_roads_m4.sqlite", Context.MODE_PRIVATE, null)
        try {
            db.execSQL("""
                INSERT INTO roads(lat1,lon1,lat2,lon2,base_kmh,oneway,name)
                VALUES(21.0,106.0,21.001,106.0,?,0,?)
            """.trimIndent(), arrayOf(speed, name))
            val id = db.rawQuery("SELECT MAX(id) FROM roads", null).use {
                assertTrue(it.moveToFirst())
                it.getLong(0)
            }
            db.execSQL("""
                INSERT INTO road_rtree(id,min_lat,max_lat,min_lon,max_lon)
                VALUES(?,21.0,21.001,106.0,106.0)
            """.trimIndent(), arrayOf(id))
        } finally {
            db.close()
        }
    }

    @Test fun auditIsReadOnlyAndCountsLegacyDuplicates() {
        OsmRoadSqliteStore.replace(context, listOf(road("Duplicate")), 1)
        injectLegacyCopy()
        val before = OsmRoadSqliteStore.audit(context)
        assertEquals(2, before.segments)
        assertEquals(1, before.fingerprintRows)
        assertEquals(2, before.spatialRows)
        assertEquals(1, before.potentialLegacyDuplicates)
        assertTrue(before.integrityOk)
        assertEquals(2, OsmRoadSqliteStore.status(context)["segments"])
    }

    @Test fun cleanupDeletesOnlyExactLegacyDuplicatesAndSpatialRows() {
        OsmRoadSqliteStore.replace(context, listOf(
            road("Duplicate"), road("Conflict", kmh = 40)), 2)
        injectLegacyCopy()
        val after = OsmRoadSqliteStore.cleanLegacyDuplicates(context)
        assertTrue(after.integrityOk)
        assertEquals(0, after.potentialLegacyDuplicates)
        assertEquals(2, after.segments)
        assertEquals(2, after.spatialRows)
        assertEquals(1, OsmRoadSqliteStore.status(context)["lastCleanedDuplicates"])
        val candidates = OsmRoadSqliteStore.nearby(
            context, 21.0005, 106.0)!!
        assertEquals(2, candidates.size)
        assertNull(OsmRoadMatcher.match(
            candidates, 21.0005, 106.0, 5.0, 0.0, 10.0))
        assertEquals(0,
            OsmRoadSqliteStore.cleanLegacyDuplicates(context)
                .potentialLegacyDuplicates)
    }

    @Test fun cleanupRefusesUnindexedUniqueRoadAndRollsBack() {
        OsmRoadSqliteStore.replace(context, listOf(road("Original")), 1)
        injectLegacyCopy(speed = 40, name = "Different")
        try {
            OsmRoadSqliteStore.cleanLegacyDuplicates(context)
            fail("Must not delete a row without an exact representative")
        } catch (_: IllegalStateException) { }
        assertEquals(2, OsmRoadSqliteStore.status(context)["segments"])
        assertEquals(2, OsmRoadSqliteStore.audit(context).spatialRows)
    }

    @Test fun cleanupRefusesCorruptedRepresentativeFingerprint() {
        OsmRoadSqliteStore.replace(context, listOf(road("Original")), 1)
        val db = context.openOrCreateDatabase(
            "osm_roads_m4.sqlite", Context.MODE_PRIVATE, null)
        try {
            db.execSQL("UPDATE road_fingerprints SET fingerprint='CORRUPTED'")
        } finally {
            db.close()
        }
        try {
            OsmRoadSqliteStore.cleanLegacyDuplicates(context)
            fail("Corrupted representative must block cleanup")
        } catch (_: IllegalStateException) {}
        assertEquals(1, OsmRoadSqliteStore.status(context)["segments"])
    }

    @Test fun auditDetectsMissingSpatialIndexRows() {
        OsmRoadSqliteStore.replace(context, listOf(road("Original")), 1)
        val db = context.openOrCreateDatabase(
            "osm_roads_m4.sqlite", Context.MODE_PRIVATE, null)
        try {
            db.execSQL("DELETE FROM road_rtree")
        } finally {
            db.close()
        }
        val report = OsmRoadSqliteStore.audit(context)
        assertFalse(report.integrityOk)
        assertEquals(1, report.missingSpatialRows)
        try {
            OsmRoadSqliteStore.cleanLegacyDuplicates(context)
            fail("Damaged spatial index should not be deleted")
        } catch (_: IllegalArgumentException) {}
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
