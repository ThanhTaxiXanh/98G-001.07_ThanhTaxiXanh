package vn.g98g00107.thanhtaxixanh.driver.car

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/**
 * M5.1: offline candidate locations, NOT authoritative road-sign warnings.
 * Imported verification claims are never trusted. The car does not render
 * these points until a separate direction/road association review is added.
 */
object TrafficPointStore {
    private const val DB_NAME = "traffic_points_m5.sqlite"
    private const val MAX_BYTES = 5 * 1024 * 1024
    const val MAX_FEATURES = 10_000
    const val MAX_TOTAL = 100_000

    private val supportedKinds = setOf(
        "speed_limit", "speed_limit_end", "urban_start", "urban_end",
        "no_left_turn", "no_right_turn", "no_u_turn", "no_entry",
        "no_overtaking", "no_parking", "camera_speed",
        "camera_red_light", "camera_other", "charging_station",
        "toll_station", "fuel_station", "rest_area"
    )

    data class Point(
        val fingerprint: String,
        val kind: String,
        val latitude: Double,
        val longitude: Double,
        val speedKmh: Int?,
        val bearing: Double?,
        val wayId: String?,
        val source: String,
        val license: String,
        val sourceRef: String?,
        val observedAt: String?
    )

    data class Parsed(val points: List<Point>, val features: Int)

    private class Helper(context: Context) :
        SQLiteOpenHelper(context.applicationContext, DB_NAME, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE traffic_points(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    fingerprint TEXT NOT NULL UNIQUE,
                    kind TEXT NOT NULL,
                    lat REAL NOT NULL, lon REAL NOT NULL,
                    speed_kmh INTEGER, bearing REAL, way_id TEXT,
                    source TEXT NOT NULL, license TEXT NOT NULL,
                    source_ref TEXT, observed_at TEXT,
                    verification TEXT NOT NULL DEFAULT 'UNVERIFIED',
                    imported_at INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX traffic_points_lat_lon ON traffic_points(lat, lon)")
            db.execSQL("CREATE INDEX traffic_points_kind ON traffic_points(kind)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            error("No traffic_points schema migration defined: $oldVersion -> $newVersion")
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun text(properties: JSONObject, key: String, max: Int, required: Boolean = false): String? {
        val value = properties.optString(key, "").trim()
        require(value.length <= max) { "$key quá dài" }
        require(!required || value.isNotBlank()) { "Thiếu $key" }
        require(!value.any { it.code < 32 }) { "$key có ký tự điều khiển" }
        return value.ifBlank { null }
    }

    fun parse(raw: String): Parsed {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "GeoJSON vượt 5 MB" }
        val root = JSONObject(raw)
        require(root.optString("type") == "FeatureCollection") { "Cần GeoJSON FeatureCollection" }
        val features = root.getJSONArray("features")
        require(features.length() in 1..MAX_FEATURES) { "Số Feature phải từ 1 đến $MAX_FEATURES" }
        val points = ArrayList<Point>()
        for (i in 0 until features.length()) {
            val f = features.getJSONObject(i)
            require(f.optString("type") == "Feature") { "Feature $i không hợp lệ" }
            val geometry = f.getJSONObject("geometry")
            require(geometry.optString("type") == "Point") { "Feature $i phải là Point (không suy diễn tâm Way)" }
            val coords = geometry.getJSONArray("coordinates")
            require(coords.length() == 2) { "Feature $i phải có [lon, lat]" }
            val lon = coords.getDouble(0)
            val lat = coords.getDouble(1)
            require(lon.isFinite() && lat.isFinite() && lat in 7.0..24.5 &&
                lon in 101.0..116.0) { "Feature $i nằm ngoài phạm vi Việt Nam" }
            val props = f.getJSONObject("properties")
            val kind = text(props, "kind", 48, true)!!
            require(kind in supportedKinds) { "Loại biển/camera chưa hỗ trợ: $kind" }
            val speed = if (props.has("speedKmh") && !props.isNull("speedKmh")) {
                val n = props.getInt("speedKmh")
                require(n in 5..130 && n % 5 == 0) { "Tốc độ không hợp lệ tại Feature $i" }
                n
            } else null
            require(kind != "speed_limit" || speed != null) { "speed_limit cần speedKmh" }
            require(kind == "speed_limit" || speed == null) { "speedKmh chỉ dành cho speed_limit" }
            val bearing = if (props.has("bearing") && !props.isNull("bearing")) {
                props.getDouble("bearing").also {
                    require(it.isFinite() && it >= 0.0 && it < 360.0) { "bearing không hợp lệ" }
                }
            } else null
            val wayId = text(props, "wayId", 48)
            val source = text(props, "source", 80, true)!!
            val license = text(props, "license", 100, true)!!
            val sourceRef = text(props, "sourceRef", 300)
            val observedAt = text(props, "observedAt", 32)
            // Round ONLY for stable duplicate identity, never for stored coordinates.
            val key = listOf(kind,
                String.format(Locale.US, "%.6f", lat),
                String.format(Locale.US, "%.6f", lon),
                speed?.toString() ?: "",
                bearing?.let { String.format(Locale.US, "%.1f", it) } ?: "",
                wayId ?: "", source, sourceRef ?: "").joinToString("|")
            points.add(Point(sha256(key), kind, lat, lon, speed, bearing,
                wayId, source, license, sourceRef, observedAt))
        }
        return Parsed(points, features.length())
    }

    @Synchronized
    fun importGeoJson(context: Context, uri: Uri, append: Boolean): Map<String, Any> {
        val raw = context.contentResolver.openInputStream(uri)?.use { input ->
            val data = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(data.size() + n <= MAX_BYTES) { "GeoJSON vượt 5 MB" }
                data.write(buffer, 0, n)
            }
            data.toString("UTF-8")
        } ?: error("Không đọc được tệp")
        return importText(context, raw, append)
    }

    /** Used by Robolectric tests; parse and validate fully BEFORE transaction. */
    @Synchronized
    fun importText(context: Context, raw: String, append: Boolean = true): Map<String, Any> {
        val parsed = parse(raw)
        val db = Helper(context).writableDatabase
        try {
            db.beginTransaction()
            try {
                if (!append) db.delete("traffic_points", null, null)
                val existing = db.rawQuery("SELECT COUNT(*) FROM traffic_points", null).use {
                    it.moveToFirst()
                    it.getInt(0)
                }
                require(existing + parsed.points.size <= MAX_TOTAL) { "Vượt $MAX_TOTAL điểm lưu trữ" }
                val now = System.currentTimeMillis()
                val insert = db.compileStatement("""
                    INSERT OR IGNORE INTO traffic_points(
                        fingerprint, kind, lat, lon, speed_kmh, bearing, way_id,
                        source, license, source_ref, observed_at, verification, imported_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'UNVERIFIED', ?)
                """.trimIndent())
                var added = 0
                try {
                    for (p in parsed.points) {
                        insert.clearBindings()
                        insert.bindString(1, p.fingerprint)
                        insert.bindString(2, p.kind)
                        insert.bindDouble(3, p.latitude)
                        insert.bindDouble(4, p.longitude)
                        if (p.speedKmh == null) insert.bindNull(5) else insert.bindLong(5, p.speedKmh.toLong())
                        if (p.bearing == null) insert.bindNull(6) else insert.bindDouble(6, p.bearing)
                        if (p.wayId == null) insert.bindNull(7) else insert.bindString(7, p.wayId)
                        insert.bindString(8, p.source)
                        insert.bindString(9, p.license)
                        if (p.sourceRef == null) insert.bindNull(10) else insert.bindString(10, p.sourceRef)
                        if (p.observedAt == null) insert.bindNull(11) else insert.bindString(11, p.observedAt)
                        insert.bindLong(12, now)
                        if (insert.executeInsert() != -1L) added++
                    }
                } finally {
                    insert.close()
                }
                db.setTransactionSuccessful()
                return summary(db) + mapOf("lastAdded" to added,
                    "lastSkipped" to (parsed.points.size - added))
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }
    }

    private fun summary(db: SQLiteDatabase): Map<String, Any> {
        var total = 0
        var verified = 0
        val counts = linkedMapOf<String, Int>()
        db.rawQuery("SELECT kind, verification, COUNT(*) FROM traffic_points GROUP BY kind, verification", null).use { cursor ->
            while (cursor.moveToNext()) {
                val kind = cursor.getString(0)
                val status = cursor.getString(1)
                val count = cursor.getInt(2)
                counts[kind] = (counts[kind] ?: 0) + count
                total += count
                if (status == "VERIFIED") verified += count
            }
        }
        return mapOf("total" to total, "unverified" to total - verified,
            "verified" to verified, "byKind" to counts,
            "source" to "User-imported point candidates; no verified on import",
            "maxFeaturesPerFile" to MAX_FEATURES, "maxTotal" to MAX_TOTAL)
    }

    @Synchronized
    fun status(context: Context): Map<String, Any> {
        val db = Helper(context).readableDatabase
        return try { summary(db) } finally { db.close() }
    }

    @Synchronized
    fun clear(context: Context): Map<String, Any> {
        val db = Helper(context).writableDatabase
        return try {
            db.delete("traffic_points", null, null)
            summary(db)
        } finally { db.close() }
    }

    /** A read-only bbox query. No UNVERIFIED point is suitable for a car warning. */
    @Synchronized
    fun candidates(context: Context, minLat: Double, minLon: Double,
                   maxLat: Double, maxLon: Double): List<Point> {
        require(minLat <= maxLat && minLon <= maxLon)
        val db = Helper(context).readableDatabase
        return try {
            val out = mutableListOf<Point>()
            db.rawQuery("""
                SELECT fingerprint,kind,lat,lon,speed_kmh,bearing,way_id,
                       source,license,source_ref,observed_at
                FROM traffic_points
                WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?
                LIMIT 500
            """.trimIndent(), arrayOf(minLat.toString(), maxLat.toString(),
                minLon.toString(), maxLon.toString())).use { c ->
                while (c.moveToNext()) {
                    out.add(Point(c.getString(0), c.getString(1), c.getDouble(2),
                        c.getDouble(3), if (c.isNull(4)) null else c.getInt(4),
                        if (c.isNull(5)) null else c.getDouble(5),
                        if (c.isNull(6)) null else c.getString(6),
                        c.getString(7), c.getString(8),
                        if (c.isNull(9)) null else c.getString(9),
                        if (c.isNull(10)) null else c.getString(10)))
                }
            }
            out
        } finally { db.close() }
    }
}
