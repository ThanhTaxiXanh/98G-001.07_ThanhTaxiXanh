package vn.g98g00107.thanhtaxixanh.driver.car

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OsmRoadTopologyMigrationTest {
    @Test fun v2DatabaseMigratesWithoutFabricatingWayOrNodeIds() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("osm_roads_m4.sqlite")
        val file = context.getDatabasePath("osm_roads_m4.sqlite")
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("""CREATE TABLE roads (
                id INTEGER PRIMARY KEY,
                lat1 REAL NOT NULL, lon1 REAL NOT NULL,
                lat2 REAL NOT NULL, lon2 REAL NOT NULL,
                base_kmh INTEGER, forward_kmh INTEGER, backward_kmh INTEGER,
                oneway INTEGER NOT NULL, name TEXT NOT NULL)""")
            db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("""CREATE TABLE road_fingerprints (
                fingerprint TEXT PRIMARY KEY NOT NULL, road_id INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE road_rtree (
                id INTEGER PRIMARY KEY, min_lat REAL NOT NULL,
                max_lat REAL NOT NULL, min_lon REAL NOT NULL,
                max_lon REAL NOT NULL)""")
            db.execSQL("""INSERT INTO roads
                (id,lat1,lon1,lat2,lon2,base_kmh,oneway,name)
                VALUES (1,21.0,106.0,21.001,106.0,60,0,'Legacy')""")
            db.version = 2
        }
        assertEquals(1, OsmRoadSqliteStore.status(context)["segments"])
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(3, db.version)
            db.rawQuery("SELECT COUNT(*) FROM road_topology", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            db.rawQuery("SELECT name FROM roads WHERE id=1", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Legacy", c.getString(0))
            }
        }
        context.deleteDatabase("osm_roads_m4.sqlite")
    }
}
