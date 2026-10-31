package vn.g98g00107.thanhtaxixanh.driver.car

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

/**
 * M4: native SQLite + RTree bounding-box index, queried off the UI thread.
 *
 * IMPORTANT: only imports vetted OSM GeoJSON maxspeed data. It does not
 * create or certify posted speed limits; road match can still return UNKNOWN.
 */
object OsmRoadSqliteStore {
    private const val DATABASE = "osm_roads_m4.sqlite"
    private const val SCHEMA = 1
    private const val MAX_MATCH_CANDIDATES = 128
    const val MAX_TOTAL_SEGMENTS = 1_000_000

    data class Summary(val segments: Int, val features: Int, val importedAt: Long)

    private class Helper(context: Context) :
        SQLiteOpenHelper(context.applicationContext, DATABASE, null, SCHEMA) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE roads (
                    id INTEGER PRIMARY KEY,
                    lat1 REAL NOT NULL, lon1 REAL NOT NULL,
                    lat2 REAL NOT NULL, lon2 REAL NOT NULL,
                    base_kmh INTEGER, forward_kmh INTEGER,
                    backward_kmh INTEGER, oneway INTEGER NOT NULL,
                    name TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            // Some Android ROMs and Robolectric SQLite builds omit SQLITE_ENABLE_RTREE.
            // A failed CREATE VIRTUAL TABLE may still reserve the table name on some
            // SQLite implementations, so ROLLBACK the DDL using a nested SAVEPOINT.
            db.execSQL("SAVEPOINT road_index_probe")
            var useRtree = false
            try {
                db.execSQL("""
                    CREATE VIRTUAL TABLE road_rtree USING rtree(
                        id, min_lat, max_lat, min_lon, max_lon
                    )
                """.trimIndent())
                db.execSQL("RELEASE SAVEPOINT road_index_probe")
                useRtree = true
            } catch (e: SQLiteException) {
                Log.w("ThanhTaxiRoadDb", "RTree unavailable; use B-tree bbox fallback", e)
                db.execSQL("ROLLBACK TO SAVEPOINT road_index_probe")
                db.execSQL("RELEASE SAVEPOINT road_index_probe")
            }
            if (useRtree) {
                db.execSQL("INSERT INTO metadata(key,value) VALUES('indexType','RTree')")
            } else {
                db.execSQL("""
                    CREATE TABLE road_rtree(
                        id INTEGER PRIMARY KEY,
                        min_lat REAL NOT NULL, max_lat REAL NOT NULL,
                        min_lon REAL NOT NULL, max_lon REAL NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX road_lat_window ON road_rtree(min_lat, max_lat)")
                db.execSQL("CREATE INDEX road_lon_window ON road_rtree(min_lon, max_lon)")
                db.execSQL("INSERT INTO metadata(key,value) VALUES('indexType','BTreeBBox')")
            }
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Never wipe speed data automatically on an unsupported schema upgrade.
            throw IllegalStateException("Unsupported road DB schema: $oldVersion to $newVersion")
        }
    }

    private fun database(context: Context) = Helper(context).writableDatabase

    /** Read-only status must be called on a worker when invoked from Flutter. */
    @Synchronized
    fun status(context: Context): Map<String, Any> {
        val db = database(context)
        return try {
            val counts = count(db)
            mapOf(
                "available" to (counts > 0),
                "segments" to counts,
                "features" to (meta(db, "features")?.toIntOrNull() ?: 0),
                "updatedAt" to (meta(db, "updatedAt")?.toLongOrNull() ?: 0L),
                "source" to "User-imported OSM maxspeed (unverified)",
                "storage" to ("SQLite " + (meta(db, "indexType") ?: "Unknown")),
                "maximumSegments" to MAX_TOTAL_SEGMENTS
            )
        } finally {
            db.close()
        }
    }

    @Synchronized
    fun importGeoJson(context: Context, uri: Uri, append: Boolean = false): Summary {
        // Preserve any older M3 GeoJSON region before extending the M4 dataset.
        if (append) migrateLegacy(context)
        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(16_384)
            val out = ByteArrayOutputStream()
            var count = 0
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                count += n
                require(count <= OsmRoadCatalog.MAX_BYTES) { "GeoJSON vượt 8 MB" }
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        } ?: throw IllegalArgumentException("Không đọc được tệp GeoJSON")
        val parsed = OsmRoadCatalog.parse(bytes.toString(Charsets.UTF_8))
        require(parsed.roads.isNotEmpty()) { "Không có đoạn đường maxspeed phù hợp" }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val summary = replace(context, parsed.roads, parsed.featureCount,
            append = append, sourceFingerprint = hash)
        // The old M3 file should not overwrite a successfully installed M4 dataset.
        File(context.filesDir, "osm_maxspeed_v1.geojson").delete()
        context.getSharedPreferences("osm_speed_import_v1", Context.MODE_PRIVATE)
            .edit().clear().apply()
        return summary
    }

    /**
     * One-time M3 -> M4 migration. If it fails the old GeoJSON remains intact,
     * while the SQLite transaction rolls back to its previous state.
     */
    @Synchronized
    fun migrateLegacy(context: Context) {
        val legacy = File(context.filesDir, "osm_maxspeed_v1.geojson")
        if (!legacy.exists()) return
        val db = database(context)
        val populated = try { count(db) > 0 } finally { db.close() }
        if (populated) {
            legacy.delete()
            return
        }
        require(legacy.length() in 1..OsmRoadCatalog.MAX_BYTES.toLong()) {
            "M3 GeoJSON size is invalid"
        }
        val parsed = OsmRoadCatalog.parse(legacy.readText(Charsets.UTF_8))
        require(parsed.roads.isNotEmpty()) { "M3 GeoJSON contains no valid roads" }
        replace(context, parsed.roads, parsed.featureCount)
        legacy.delete()
        context.getSharedPreferences("osm_speed_import_v1", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @Synchronized
    fun replace(context: Context, roads: List<OsmRoadSegment>,
                featureCount: Int, append: Boolean = false,
                sourceFingerprint: String? = null): Summary {
        require(roads.isNotEmpty() && roads.size <= OsmRoadCatalog.MAX_SEGMENTS) {
            "Số lượng đoạn đường ngoài giới hạn M4"
        }
        val db = database(context)
        val now = System.currentTimeMillis()
        var finalCount = 0
        var finalFeatures = 0
        try {
            val hashKey = sourceFingerprint?.let { "importSha:$it" }
            val oldCount = count(db)
            val oldFeatures = if (append) meta(db, "features")?.toIntOrNull() ?: 0 else 0
            if (append && hashKey != null && meta(db, hashKey) != null) {
                return Summary(oldCount, oldFeatures,
                    meta(db, "updatedAt")?.toLongOrNull() ?: 0L)
            }
            require(!append || oldCount.toLong() + roads.size <= MAX_TOTAL_SEGMENTS) {
                "Vượt hạn mức 1.000.000 đoạn đường"
            }
            db.beginTransaction()
            try {
                if (!append) {
                    db.execSQL("DELETE FROM road_rtree")
                    db.execSQL("DELETE FROM roads")
                    db.execSQL("DELETE FROM metadata WHERE key != 'indexType'")
                }
                val insertRoad = db.compileStatement("""
                    INSERT INTO roads
                    (lat1,lon1,lat2,lon2,base_kmh,forward_kmh,backward_kmh,oneway,name)
                    VALUES (?,?,?,?,?,?,?,?,?)
                """.trimIndent())
                val insertIndex = db.compileStatement("""
                    INSERT INTO road_rtree
                    (id,min_lat,max_lat,min_lon,max_lon) VALUES (?,?,?,?,?)
                """.trimIndent())
                try {
                    for (road in roads) {
                        insertRoad.clearBindings()
                        insertRoad.bindDouble(1, road.lat1)
                        insertRoad.bindDouble(2, road.lon1)
                        insertRoad.bindDouble(3, road.lat2)
                        insertRoad.bindDouble(4, road.lon2)
                        road.generalKmh?.let { insertRoad.bindLong(5, it.toLong()) }
                        road.forwardKmh?.let { insertRoad.bindLong(6, it.toLong()) }
                        road.backwardKmh?.let { insertRoad.bindLong(7, it.toLong()) }
                        insertRoad.bindLong(8, road.oneway.toLong())
                        insertRoad.bindString(9, road.name)
                        val id = insertRoad.executeInsert()
                        check(id > 0) { "Cannot insert OSM road" }

                        insertIndex.clearBindings()
                        insertIndex.bindLong(1, id)
                        insertIndex.bindDouble(2, minOf(road.lat1, road.lat2))
                        insertIndex.bindDouble(3, maxOf(road.lat1, road.lat2))
                        insertIndex.bindDouble(4, minOf(road.lon1, road.lon2))
                        insertIndex.bindDouble(5, maxOf(road.lon1, road.lon2))
                        insertIndex.executeInsert()
                    }
                } finally {
                    insertRoad.close()
                    insertIndex.close()
                }
                finalCount = (if (append) oldCount else 0) + roads.size
                finalFeatures = oldFeatures + featureCount
                db.execSQL("INSERT OR REPLACE INTO metadata(key,value) VALUES('features',?)",
                    arrayOf(finalFeatures.toString()))
                db.execSQL("INSERT OR REPLACE INTO metadata(key,value) VALUES('updatedAt',?)",
                    arrayOf(now.toString()))
                hashKey?.let { key ->
                    db.execSQL("INSERT OR REPLACE INTO metadata(key,value) VALUES(?,?)",
                        arrayOf(key, now.toString()))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }
        return Summary(finalCount, finalFeatures, now)
    }

    /**
     * RTree prefilter for a 30m vicinity. If >128 segments intersect the box,
     * return no candidates instead of silently truncating ambiguous roads.
     */
    @Synchronized
    fun nearby(context: Context, latitude: Double, longitude: Double):
        List<OsmRoadSegment>? {
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val latDelta = 32.0 / 111195.0
        val scale = abs(cos(latitude * PI / 180.0))
        if (scale < 0.01) return null
        val lonDelta = 32.0 / (111195.0 * scale)
        val db = database(context)
        try {
            val cursor = db.rawQuery("""
                SELECT r.lat1, r.lon1, r.lat2, r.lon2,
                       r.base_kmh, r.forward_kmh, r.backward_kmh, r.oneway, r.name
                FROM road_rtree AS i
                JOIN roads AS r ON r.id = i.id
                WHERE i.min_lat <= ? AND i.max_lat >= ?
                  AND i.min_lon <= ? AND i.max_lon >= ?
                LIMIT ?
            """.trimIndent(), arrayOf(
                (latitude + latDelta).toString(),
                (latitude - latDelta).toString(),
                (longitude + lonDelta).toString(),
                (longitude - lonDelta).toString(),
                (MAX_MATCH_CANDIDATES + 1).toString()
            ))
            return cursor.use {
                val list = ArrayList<OsmRoadSegment>()
                while (it.moveToNext()) {
                    if (list.size >= MAX_MATCH_CANDIDATES) return null
                    list.add(OsmRoadSegment(
                        it.getDouble(0), it.getDouble(1),
                        it.getDouble(2), it.getDouble(3),
                        if (it.isNull(4)) null else it.getInt(4),
                        if (it.isNull(5)) null else it.getInt(5),
                        if (it.isNull(6)) null else it.getInt(6),
                        it.getInt(7), it.getString(8) ?: ""
                    ))
                }
                list
            }
        } finally {
            db.close()
        }
    }

    @Synchronized
    fun clear(context: Context) {
        // Delete the contents transactionally, preserving a valid SQLite schema.
        val db = database(context)
        try {
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM road_rtree")
                db.execSQL("DELETE FROM roads")
                db.execSQL("DELETE FROM metadata WHERE key != 'indexType'")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }
        File(context.filesDir, "osm_maxspeed_v1.geojson").delete()
        context.getSharedPreferences("osm_speed_import_v1", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun count(db: SQLiteDatabase): Int {
        return db.rawQuery("SELECT COUNT(*) FROM roads", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun meta(db: SQLiteDatabase, key: String): String? =
        db.rawQuery("SELECT value FROM metadata WHERE key=?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }
}
