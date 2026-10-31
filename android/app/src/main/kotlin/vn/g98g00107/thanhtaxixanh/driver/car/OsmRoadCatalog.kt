package vn.g98g00107.thanhtaxixanh.driver.car

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Offline import of a SMALL region's OSM road GeoJSON; not a national-scale database. */
object OsmRoadCatalog {
    private const val STORAGE = "osm_maxspeed_v1.geojson"
    private const val PREFS = "osm_speed_import_v1"
    const val MAX_BYTES = 8_000_000
    const val MAX_SEGMENTS = 50_000

    data class Snapshot(val roads: List<OsmRoadSegment>, val featureCount: Int)
    data class Summary(val segments: Int, val features: Int, val importedAt: Long)

    private fun file(context: Context) = File(context.filesDir, STORAGE)

    fun status(context: Context): Map<String, Any> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val present = file(context).exists()
        return mapOf(
            "available" to present,
            "segments" to if (present) prefs.getInt("segments", 0) else 0,
            "features" to if (present) prefs.getInt("features", 0) else 0,
            "updatedAt" to if (present) prefs.getLong("updatedAt", 0L) else 0L,
            "source" to "User-provided OSM GeoJSON (unverified tags)"
        )
    }

    fun load(context: Context): Snapshot {
        val path = file(context)
        if (!path.exists()) return Snapshot(emptyList(), 0)
        require(path.length() in 1..MAX_BYTES.toLong()) { "GeoJSON size out of range" }
        return parse(path.readText(Charsets.UTF_8))
    }

    /** Import is atomic: invalid/oversized uploads leave the previous dataset intact. */
    fun import(context: Context, uri: Uri): Summary {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Không đọc được tệp GeoJSON")
        val bytes = input.use { source ->
            val out = ByteArrayOutputStream()
            val block = ByteArray(16_384)
            var count = 0
            while (true) {
                val got = source.read(block)
                if (got < 0) break
                count += got
                require(count <= MAX_BYTES) { "Tệp quá lớn (giới hạn 8 MB)" }
                out.write(block, 0, got)
            }
            out.toByteArray()
        }
        val raw = bytes.toString(Charsets.UTF_8)
        val snapshot = parse(raw)
        require(snapshot.roads.isNotEmpty()) { "Không có LineString với maxspeed hợp lệ" }
        val temp = File(context.filesDir, "$STORAGE.tmp")
        temp.writeBytes(bytes)
        try {
            Files.move(temp.toPath(), file(context).toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (temp.exists()) temp.delete()
        }
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("segments", snapshot.roads.size)
            .putInt("features", snapshot.featureCount)
            .putLong("updatedAt", now)
            .apply()
        return Summary(snapshot.roads.size, snapshot.featureCount, now)
    }

    fun clear(context: Context) {
        check(!file(context).exists() || file(context).delete()) { "Cannot remove road dataset" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    internal fun parse(text: String): Snapshot {
        val root = JSONObject(text)
        require(root.optString("type") == "FeatureCollection") {
            "Tệp phải là GeoJSON FeatureCollection"
        }
        val features = root.optJSONArray("features")
            ?: throw IllegalArgumentException("GeoJSON thiếu danh sách features")
        require(features.length() <= 100_000) { "Quá nhiều features" }
        val roads = ArrayList<OsmRoadSegment>()
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val properties = feature.optJSONObject("properties") ?: continue
            // Conditional maxspeed may differ by time/weather; never silently ignore it.
            if (properties.has("maxspeed:conditional") ||
                properties.has("maxspeed:forward:conditional") ||
                properties.has("maxspeed:backward:conditional")) continue
            val base = numericLimit(properties.opt("maxspeed"))
            val forward = numericLimit(properties.opt("maxspeed:forward"))
            val backward = numericLimit(properties.opt("maxspeed:backward"))
            if (base == null && forward == null && backward == null) continue
            val oneway = when (properties.optString("oneway").lowercase()) {
                "yes", "true", "1" -> 1
                "-1", "reverse" -> -1
                else -> 0
            }
            val roadName = properties.optString("name").take(70)
            val geometry = feature.optJSONObject("geometry") ?: continue
            val geometryType = geometry.optString("type")
            val lines = when (geometryType) {
                "LineString" -> listOfNotNull(geometry.optJSONArray("coordinates"))
                "MultiLineString" -> {
                    val list = ArrayList<JSONArray>()
                    val many = geometry.optJSONArray("coordinates") ?: continue
                    for (j in 0 until many.length()) {
                        many.optJSONArray(j)?.let(list::add)
                    }
                    list
                }
                else -> continue
            }
            for (line in lines) {
                // GeoJSON coordinates are [longitude, latitude] in WGS84.
                for (j in 1 until line.length()) {
                    val start = point(line.optJSONArray(j - 1)) ?: continue
                    val end = point(line.optJSONArray(j)) ?: continue
                    roads.add(OsmRoadSegment(
                        start.second, start.first, end.second, end.first,
                        base, forward, backward, oneway, roadName
                    ))
                    require(roads.size <= MAX_SEGMENTS) {
                        "Quá nhiều đoạn đường (giới hạn 50.000)"
                    }
                }
            }
        }
        return Snapshot(roads, features.length())
    }

    private fun point(array: JSONArray?): Pair<Double, Double>? {
        if (array == null || array.length() < 2) return null
        val lon = array.optDouble(0, Double.NaN)
        val lat = array.optDouble(1, Double.NaN)
        return if (lat.isFinite() && lon.isFinite() &&
            lat in -90.0..90.0 && lon in -180.0..180.0) Pair(lon, lat)
            else null
    }

    private fun numericLimit(value: Any?): Int? {
        if (value == null || value == JSONObject.NULL) return null
        val raw = value.toString().trim()
        val matches = Regex("^([0-9]{1,3})(?:\\s*(?:km/h|kmh|kph))?$",
            RegexOption.IGNORE_CASE).matchEntire(raw) ?: return null
        val speed = matches.groupValues[1].toIntOrNull() ?: return null
        // Conservative filter for road-level car maxspeed data in Vietnam.
        return speed.takeIf { it in 10..130 }
    }
}
