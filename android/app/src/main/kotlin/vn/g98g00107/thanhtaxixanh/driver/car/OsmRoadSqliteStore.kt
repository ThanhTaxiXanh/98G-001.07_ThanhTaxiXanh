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
    private const val SCHEMA = 3
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
            OsmRoadSqliteStore.createFingerprintTable(db)
            OsmRoadSqliteStore.createTopologyTable(db)
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
            require(newVersion == 3 && oldVersion in 1..2) {
                "Unsupported road DB schema: $oldVersion to $newVersion"
            }
            if (oldVersion == 1) {
                OsmRoadSqliteStore.createFingerprintTable(db)
                OsmRoadSqliteStore.backfillFingerprints(db)
            }
            // Old rows deliberately remain WITHOUT topology identifiers.
            // No way/node IDs are guessed from geometry.
            OsmRoadSqliteStore.createTopologyTable(db)
        }
    }

    private fun database(context: Context) = Helper(context).writableDatabase

    private fun createFingerprintTable(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS road_fingerprints (
                fingerprint TEXT PRIMARY KEY NOT NULL,
                road_id INTEGER NOT NULL
            )
        """.trimIndent())
    }

    /** Optional topology sidecar. Empty for legacy and current GeoJSON imports.
     * A future verified OSM-node import must populate this transactionally.
     */
    private fun createTopologyTable(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS road_topology (
                road_id INTEGER PRIMARY KEY NOT NULL,
                osm_way_id TEXT NOT NULL,
                from_osm_node TEXT NOT NULL,
                to_osm_node TEXT NOT NULL,
                layer INTEGER,
                bridge INTEGER NOT NULL DEFAULT 0,
                tunnel INTEGER NOT NULL DEFAULT 0,
                source TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS road_topology_way ON road_topology(osm_way_id)")
    }

    /** Version 1 had no segment keys; preserve all old roads while indexing
     * one representative per identical geometry+tags. Subsequent imports
     * will skip duplicates, but conflicting maxspeed remains a separate row.
     */
    private fun backfillFingerprints(db: SQLiteDatabase) {
        val insert = db.compileStatement("""
            INSERT OR IGNORE INTO road_fingerprints(fingerprint,road_id)
            VALUES (?,?)
        """.trimIndent())
        try {
            db.rawQuery("""
                SELECT id,lat1,lon1,lat2,lon2,base_kmh,
                       forward_kmh,backward_kmh,oneway,name FROM roads
            """.trimIndent(), null).use { cursor ->
                while (cursor.moveToNext()) {
                    val road = OsmRoadSegment(
                        cursor.getDouble(1), cursor.getDouble(2),
                        cursor.getDouble(3), cursor.getDouble(4),
                        if (cursor.isNull(5)) null else cursor.getInt(5),
                        if (cursor.isNull(6)) null else cursor.getInt(6),
                        if (cursor.isNull(7)) null else cursor.getInt(7),
                        cursor.getInt(8), cursor.getString(9) ?: ""
                    )
                    insert.clearBindings()
                    insert.bindString(1, OsmRoadFingerprint.sha256(road))
                    insert.bindLong(2, cursor.getLong(0))
                    insert.executeInsert()
                }
            }
        } finally {
            insert.close()
        }
    }

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
                "maximumSegments" to MAX_TOTAL_SEGMENTS,
                "lastAdded" to (meta(db, "lastAdded")?.toIntOrNull() ?: 0),
                "lastSkippedDuplicates" to (meta(db, "lastSkippedDuplicates")?.toIntOrNull() ?: 0),
                "lastCleanedDuplicates" to (meta(db, "lastCleanedDuplicates")?.toIntOrNull() ?: 0),
                "lastCleanedAt" to (meta(db, "lastCleanedAt")?.toLongOrNull() ?: 0L)
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
                sourceFingerprint: String? = null,
                topology: List<OsmTopologyParser.Link>? = null): Summary {
        require(roads.isNotEmpty() && roads.size <= OsmRoadCatalog.MAX_SEGMENTS) {
            "Số lượng đoạn đường ngoài giới hạn M4"
        }
        require(topology == null || (!append && topology.size == roads.size)) {
            "Topology requires a full replacement with one link per road"
        }
        val db = database(context)
        val now = System.currentTimeMillis()
        var finalCount = 0
        var finalFeatures = 0
        var inserted = 0
        var skipped = 0
        try {
            val hashKey = sourceFingerprint?.let { "importSha:$it" }
            val oldCount = count(db)
            val oldFeatures = if (append) meta(db, "features")?.toIntOrNull() ?: 0 else 0
            if (append && hashKey != null && meta(db, hashKey) != null) {
                return Summary(oldCount, oldFeatures,
                    meta(db, "updatedAt")?.toLongOrNull() ?: 0L)
            }
            db.beginTransaction()
            try {
                if (!append) {
                    db.execSQL("DELETE FROM road_rtree")
                    db.execSQL("DELETE FROM road_topology")
                    db.execSQL("DELETE FROM roads")
                    db.execSQL("DELETE FROM road_fingerprints")
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
                val reserveFingerprint = db.compileStatement("""
                    INSERT OR IGNORE INTO road_fingerprints(fingerprint,road_id)
                    VALUES (?,0)
                """.trimIndent())
                val updateFingerprint = db.compileStatement("""
                    UPDATE road_fingerprints SET road_id=? WHERE fingerprint=?
                """.trimIndent())
                val insertTopology = if (topology != null) db.compileStatement("""
                    INSERT INTO road_topology
                    (road_id,osm_way_id,from_osm_node,to_osm_node,layer,bridge,tunnel,source)
                    VALUES (?,?,?,?,?,?,?,?)
                """.trimIndent()) else null
                try {
                    for ((roadIndex, road) in roads.withIndex()) {
                        val fingerprint = OsmRoadFingerprint.sha256(road)
                        reserveFingerprint.clearBindings()
                        reserveFingerprint.bindString(1, fingerprint)
                        if (reserveFingerprint.executeInsert() == -1L) {
                            require(topology == null) { "Duplicate topology geometry; refusing ambiguous import" }
                            skipped++
                            continue
                        }
                        // Limit *unique* segments, not raw input segments.
                        require((if (append) oldCount else 0) + inserted < MAX_TOTAL_SEGMENTS) {
                            "Vượt hạn mức 1.000.000 đoạn đường"
                        }
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
                        updateFingerprint.clearBindings()
                        updateFingerprint.bindLong(1, id)
                        updateFingerprint.bindString(2, fingerprint)
                        check(updateFingerprint.executeUpdateDelete() == 1) {
                            "Cannot link road fingerprint"
                        }

                        insertIndex.clearBindings()
                        insertIndex.bindLong(1, id)
                        insertIndex.bindDouble(2, minOf(road.lat1, road.lat2))
                        insertIndex.bindDouble(3, maxOf(road.lat1, road.lat2))
                        insertIndex.bindDouble(4, minOf(road.lon1, road.lon2))
                        insertIndex.bindDouble(5, maxOf(road.lon1, road.lon2))
                        insertIndex.executeInsert()
                        if (insertTopology != null) {
                            val link = topology!![roadIndex]
                            require(link.wayId.matches(Regex("[1-9][0-9]{0,18}")) &&
                                link.fromNode.matches(Regex("[1-9][0-9]{0,18}")) &&
                                link.toNode.matches(Regex("[1-9][0-9]{0,18}")) &&
                                link.fromNode != link.toNode &&
                                link.source.isNotBlank() && link.source.length <= 256)
                            insertTopology.clearBindings()
                            insertTopology.bindLong(1, id)
                            insertTopology.bindString(2, link.wayId)
                            insertTopology.bindString(3, link.fromNode)
                            insertTopology.bindString(4, link.toNode)
                            link.layer?.let { insertTopology.bindLong(5, it.toLong()) }
                            insertTopology.bindLong(6, if (link.bridge) 1 else 0)
                            insertTopology.bindLong(7, if (link.tunnel) 1 else 0)
                            insertTopology.bindString(8, link.source)
                            check(insertTopology.executeInsert() > 0)
                        }
                        inserted++
                    }
                } finally {
                    insertRoad.close()
                    insertIndex.close()
                    reserveFingerprint.close()
                    updateFingerprint.close()
                    insertTopology?.close()
                }
                finalCount = (if (append) oldCount else 0) + inserted
                finalFeatures = oldFeatures + (if (inserted > 0) featureCount else 0)
                db.execSQL("INSERT OR REPLACE INTO metadata(key,value) VALUES('features',?)",
                    arrayOf(finalFeatures.toString()))
                db.execSQL("INSERT OR REPLACE INTO metadata(key,value) VALUES('updatedAt',?)",
                    arrayOf(now.toString()))
                db.execSQL("INSERT OR REPLACE INTO metadata(key,value) VALUES('lastAdded',?)",
                    arrayOf(inserted.toString()))
                db.execSQL("INSERT OR REPLACE INTO metadata(key,value) VALUES('lastSkippedDuplicates',?)",
                    arrayOf(skipped.toString()))
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

    /** M5.3J: full replacement only. Parse before opening the database;
     * write roads, fingerprints, spatial index and topology atomically.
     */
    @Synchronized
    fun importTopologyGeoJson(context: Context, geoJson: String,
                              source: String): Summary {
        val plan = OsmTopologyImportPlan.prepare(geoJson, source)
        return replace(context, plan.entries.map { it.first },
            plan.entries.size + plan.skippedNonSpeedFeatures,
            topology = plan.entries.map { it.second })
    }

    /**
     * M5.3E: attach explicit topology to one EXACT existing road geometry.
     * This is not an automatic import: caller must provide a trusted segment
     * and provenance. Ambiguous or duplicate geometry fails closed.
     * All links are committed or rolled back as one transaction.
     */
    @Synchronized
    fun attachTopology(context: Context,
                       entries: List<Pair<OsmRoadSegment, OsmTopologyParser.Link>>): Int {
        require(entries.isNotEmpty() && entries.size <= 50_000)
        val db = database(context)
        try {
            db.beginTransaction()
            try {
                val seen = HashSet<Long>()
                val stmt = db.compileStatement("""
                    INSERT INTO road_topology
                    (road_id,osm_way_id,from_osm_node,to_osm_node,layer,bridge,tunnel,source)
                    VALUES (?,?,?,?,?,?,?,?)
                """.trimIndent())
                try {
                    for ((road, link) in entries) {
                        require(link.wayId.matches(Regex("[1-9][0-9]{0,18}")) &&
                            link.fromNode.matches(Regex("[1-9][0-9]{0,18}")) &&
                            link.toNode.matches(Regex("[1-9][0-9]{0,18}")) &&
                            link.fromNode != link.toNode &&
                            link.source.isNotBlank() && link.source.length <= 256)
                        val ids = ArrayList<Long>()
                        db.rawQuery("""
                            SELECT id FROM roads
                            WHERE lat1=? AND lon1=? AND lat2=? AND lon2=?
                              AND COALESCE(base_kmh,-1)=?
                              AND COALESCE(forward_kmh,-1)=?
                              AND COALESCE(backward_kmh,-1)=?
                              AND oneway=? AND name=?
                            LIMIT 2
                        """.trimIndent(), arrayOf(
                            road.lat1.toString(), road.lon1.toString(),
                            road.lat2.toString(), road.lon2.toString(),
                            (road.generalKmh ?: -1).toString(),
                            (road.forwardKmh ?: -1).toString(),
                            (road.backwardKmh ?: -1).toString(),
                            road.oneway.toString(), road.name
                        )).use { cursor ->
                            while (cursor.moveToNext()) ids.add(cursor.getLong(0))
                        }
                        require(ids.size == 1) { "Topology geometry not unique or missing" }
                        val id = ids.single()
                        require(seen.add(id)) { "Duplicate road topology mapping" }
                        stmt.clearBindings()
                        stmt.bindLong(1, id)
                        stmt.bindString(2, link.wayId)
                        stmt.bindString(3, link.fromNode)
                        stmt.bindString(4, link.toNode)
                        if (link.layer != null) stmt.bindLong(5, link.layer.toLong())
                        stmt.bindLong(6, if (link.bridge) 1 else 0)
                        stmt.bindLong(7, if (link.tunnel) 1 else 0)
                        stmt.bindString(8, link.source)
                        check(stmt.executeInsert() > 0) { "Topology already linked" }
                    }
                } finally {
                    stmt.close()
                }
                db.setTransactionSuccessful()
                return seen.size
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }
    }

    /** M5.3G: read ONLY explicitly linked topology rows; never infer node IDs.
     * This is a bounded offline review API, not a verified speed source.
     */
    @Synchronized
    fun topologyGraph(context: Context, limit: Int = 50_000):
        OsmTopologyGraphBuilder.Build {
        require(limit in 1..50_000)
        val db = database(context)
        try {
            val segments = ArrayList<OsmTopologyGraphBuilder.Segment>()
            db.rawQuery("""
                SELECT r.id, t.from_osm_node, t.to_osm_node, t.osm_way_id,
                       r.lat1, r.lon1, r.lat2, r.lon2, r.oneway
                FROM road_topology AS t
                JOIN roads AS r ON r.id = t.road_id
                ORDER BY r.id LIMIT ?
            """.trimIndent(), arrayOf((limit + 1).toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    require(segments.size < limit) {
                        "Topology exceeds review limit; refusing truncated graph"
                    }
                    val lat1 = cursor.getDouble(4)
                    val lon1 = cursor.getDouble(5)
                    val lat2 = cursor.getDouble(6)
                    val lon2 = cursor.getDouble(7)
                    val meanLat = (lat1 + lat2) / 2.0 * PI / 180.0
                    val north = (lat2 - lat1) * 111_195.0
                    val east = (lon2 - lon1) * 111_195.0 * cos(meanLat)
                    val lengthM = kotlin.math.hypot(north, east)
                    segments.add(OsmTopologyGraphBuilder.Segment(
                        cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), lengthM, cursor.getInt(8)))
                }
            }
            return OsmTopologyGraphBuilder.build(segments)
        } finally {
            db.close()
        }
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


    data class QualityReport(
        val segments: Int,
        val fingerprintRows: Int,
        val spatialRows: Int,
        val potentialLegacyDuplicates: Int,
        val brokenFingerprints: Int,
        val missingSpatialRows: Int,
        val orphanSpatialRows: Int
    ) {
        val integrityOk: Boolean
            get() = brokenFingerprints == 0 && missingSpatialRows == 0 &&
                orphanSpatialRows == 0 && fingerprintRows <= segments

        fun asMap(): Map<String, Any> = mapOf(
            "segments" to segments,
            "fingerprints" to fingerprintRows,
            "spatialRows" to spatialRows,
            "legacyDuplicates" to potentialLegacyDuplicates,
            "brokenFingerprints" to brokenFingerprints,
            "missingSpatialRows" to missingSpatialRows,
            "orphanSpatialRows" to orphanSpatialRows,
            "integrityOk" to integrityOk
        )
    }

    private fun countQuery(db: SQLiteDatabase, sql: String): Int =
        db.rawQuery(sql, null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    /**
     * M4.3 read-only integrity audit. This does NOT infer speed-limit truth.
     * Duplicate count is based on the v2 canonical-fingerprint representative.
     * A damaged fingerprint index will be flagged instead of silently repaired.
     */
    private fun auditDatabase(db: SQLiteDatabase): QualityReport {
        val segments = count(db)
        val fingerprints = countQuery(db, "SELECT COUNT(*) FROM road_fingerprints")
        val spatial = countQuery(db, "SELECT COUNT(*) FROM road_rtree")
        val broken = countQuery(db, """
            SELECT COUNT(*) FROM road_fingerprints f
            LEFT JOIN roads r ON f.road_id=r.id WHERE r.id IS NULL
        """.trimIndent())
        val missing = countQuery(db, """
            SELECT COUNT(*) FROM roads r
            LEFT JOIN road_rtree i ON i.id=r.id WHERE i.id IS NULL
        """.trimIndent())
        val orphans = countQuery(db, """
            SELECT COUNT(*) FROM road_rtree i
            LEFT JOIN roads r ON r.id=i.id WHERE r.id IS NULL
        """.trimIndent())
        return QualityReport(
            segments, fingerprints, spatial,
            (segments - fingerprints).coerceAtLeast(0),
            broken, missing, orphans
        )
    }

    @Synchronized
    fun audit(context: Context): QualityReport {
        val db = database(context)
        return try {
            auditDatabase(db)
        } finally {
            db.close()
        }
    }

    /**
     * Explicit, user-triggered M4.3 cleanup. Only exact canonical duplicates
     * left behind by M4.1 are eligible. No speed conflict is ever resolved.
     *
     * Verify that every representative and candidate fingerprint matches
     * before deleting anything. SQL savepoints are handled by the outer
     * transaction: any failure keeps all original roads and spatial rows.
     */
    @Synchronized
    fun cleanLegacyDuplicates(context: Context): QualityReport {
        val db = database(context)
        try {
            db.beginTransaction()
            try {
                val before = auditDatabase(db)
                require(before.integrityOk) {
                    "Chỉ mục dữ liệu bị lỗi; không thể tự dọn. Hãy sao lưu và kiểm tra."
                }
                // A corrupted fingerprint table must never authorize deletion.
                db.rawQuery("""
                    SELECT f.fingerprint,r.lat1,r.lon1,r.lat2,r.lon2,
                           r.base_kmh,r.forward_kmh,r.backward_kmh,r.oneway,r.name
                    FROM road_fingerprints f JOIN roads r ON f.road_id=r.id
                """.trimIndent(), null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val segment = OsmRoadSegment(
                            cursor.getDouble(1), cursor.getDouble(2),
                            cursor.getDouble(3), cursor.getDouble(4),
                            if (cursor.isNull(5)) null else cursor.getInt(5),
                            if (cursor.isNull(6)) null else cursor.getInt(6),
                            if (cursor.isNull(7)) null else cursor.getInt(7),
                            cursor.getInt(8), cursor.getString(9) ?: ""
                        )
                        check(cursor.getString(0) == OsmRoadFingerprint.sha256(segment)) {
                            "Sai định danh đoạn đường; đã hủy dọn dữ liệu."
                        }
                    }
                }
                var removed = 0
                if (before.potentialLegacyDuplicates > 0) {
                    db.execSQL("""
                        CREATE TEMP TABLE IF NOT EXISTS cleanup_duplicate_ids(
                            id INTEGER PRIMARY KEY
                        )
                    """.trimIndent())
                    db.execSQL("DELETE FROM temp.cleanup_duplicate_ids")
                    val insert = db.compileStatement(
                        "INSERT INTO temp.cleanup_duplicate_ids(id) VALUES(?)")
                    try {
                        db.rawQuery("""
                            SELECT r.id,r.lat1,r.lon1,r.lat2,r.lon2,
                                   r.base_kmh,r.forward_kmh,r.backward_kmh,
                                   r.oneway,r.name
                            FROM roads r
                            WHERE NOT EXISTS (
                                SELECT 1 FROM road_fingerprints f WHERE f.road_id=r.id
                            ) ORDER BY r.id
                        """.trimIndent(), null).use { cursor ->
                            while (cursor.moveToNext()) {
                                val segment = OsmRoadSegment(
                                    cursor.getDouble(1), cursor.getDouble(2),
                                    cursor.getDouble(3), cursor.getDouble(4),
                                    if (cursor.isNull(5)) null else cursor.getInt(5),
                                    if (cursor.isNull(6)) null else cursor.getInt(6),
                                    if (cursor.isNull(7)) null else cursor.getInt(7),
                                    cursor.getInt(8), cursor.getString(9) ?: ""
                                )
                                val fingerprint = OsmRoadFingerprint.sha256(segment)
                                val exists = db.rawQuery(
                                    "SELECT 1 FROM road_fingerprints WHERE fingerprint=?",
                                    arrayOf(fingerprint)
                                ).use { it.moveToFirst() }
                                check(exists) {
                                    "Phát hiện đoạn đường chưa được lập chỉ mục; không xóa."
                                }
                                insert.clearBindings()
                                insert.bindLong(1, cursor.getLong(0))
                                check(insert.executeInsert() > 0) {
                                    "Không tạo được danh sách dọn trùng"
                                }
                                removed++
                            }
                        }
                    } finally {
                        insert.close()
                    }
                    check(removed == before.potentialLegacyDuplicates) {
                        "Kiểm đếm bản sao không nhất quán; đã hủy thao tác."
                    }
                    db.execSQL("""
                        DELETE FROM road_rtree WHERE id IN (
                            SELECT id FROM temp.cleanup_duplicate_ids
                        )
                    """.trimIndent())
                    db.execSQL("""
                        DELETE FROM roads WHERE id IN (
                            SELECT id FROM temp.cleanup_duplicate_ids
                        )
                    """.trimIndent())
                    db.execSQL("DELETE FROM temp.cleanup_duplicate_ids")
                }
                val after = auditDatabase(db)
                check(after.integrityOk && after.potentialLegacyDuplicates == 0 &&
                    after.segments == before.segments - removed) {
                    "Kết quả sau dọn không nhất quán; đã hoàn tác."
                }
                db.execSQL("""
                    INSERT OR REPLACE INTO metadata(key,value)
                    VALUES('lastCleanedDuplicates',?)
                """.trimIndent(), arrayOf(removed.toString()))
                db.execSQL("""
                    INSERT OR REPLACE INTO metadata(key,value)
                    VALUES('lastCleanedAt',?)
                """.trimIndent(), arrayOf(System.currentTimeMillis().toString()))
                db.setTransactionSuccessful()
                return after
            } finally {
                db.endTransaction()
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
                db.execSQL("DELETE FROM road_fingerprints")
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
