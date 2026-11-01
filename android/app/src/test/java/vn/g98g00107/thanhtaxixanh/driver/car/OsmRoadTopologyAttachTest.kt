package vn.g98g00107.thanhtaxixanh.driver.car

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OsmRoadTopologyAttachTest {
    private lateinit var context: Context
    private val road = OsmRoadSegment(21.0,106.0,21.001,106.0,60,null,null,0,"A")
    private val link = OsmTopologyParser.Link("123","11","12",null,false,false,"OSM source")
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        OsmRoadSqliteStore.clear(context)
    }
    private fun countTopology(): Int {
        val file = context.getDatabasePath("osm_roads_m4.sqlite")
        return SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM road_topology",null).use { c ->
                c.moveToFirst()
                c.getInt(0)
            }
        }
    }
    @Test fun attachesOnlyExactGeometry() {
        OsmRoadSqliteStore.replace(context,listOf(road),1)
        assertEquals(1,OsmRoadSqliteStore.attachTopology(context,listOf(road to link)))
        assertEquals(1,countTopology())
    }
    @Test fun mismatchRollsBackWholeBatch() {
        OsmRoadSqliteStore.replace(context,listOf(road),1)
        try {
            OsmRoadSqliteStore.attachTopology(context,listOf(
                road to link, road.copy(lat1 = 21.2) to link.copy(wayId = "124")))
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {}
        assertEquals(0,countTopology())
    }
    @Test fun duplicateMappingRollsBack() {
        OsmRoadSqliteStore.replace(context,listOf(road),1)
        try {
            OsmRoadSqliteStore.attachTopology(context,listOf(road to link,road to link))
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {}
        assertEquals(0,countTopology())
    }
}
