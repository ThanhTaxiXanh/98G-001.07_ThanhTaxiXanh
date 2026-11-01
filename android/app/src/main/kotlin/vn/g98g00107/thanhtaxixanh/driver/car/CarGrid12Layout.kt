package vn.g98g00107.thanhtaxixanh.driver.car

import android.content.Context
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads the same Flutter SharedPreferences key as LayoutStore.
 * Layout is always projected into the host's safe Surface, not phone preview pixels.
 */
object CarGrid12Layout {
    data class Tile(val name: String, val x: Int, val y: Int, val w: Int, val h: Int)
    private fun t(name: String, x: Int, y: Int, w: Int, h: Int) =
        Tile(name, x, y, w, h)

    private fun preset(id: String): List<Tile> {
        val rail = t("icons", 0, 0, 1, 12)
        val map = t("map", 1, 0, 6, 12)
        return listOf(rail) + when (id) {
            "stacked" -> listOf(map, t("calendar",7,0,5,4),
                t("qr",7,4,5,4),t("media",7,8,5,4))
            "calendar_first" -> listOf(map,t("calendar",7,0,5,6),
                t("qr",7,6,5,4),t("media",7,10,5,2))
            "qr_first" -> listOf(map,t("qr",7,0,5,7),
                t("calendar",7,7,5,3),t("media",7,10,5,2))
            "wide_map" -> listOf(t("map",1,0,8,12),t("calendar",9,0,3,4),
                t("qr",9,4,3,5),t("media",9,9,3,3))
            "wide_tools" -> listOf(t("map",1,0,5,12),t("calendar",6,0,6,3),
                t("qr",6,3,6,7),t("media",6,10,6,2))
            "bottom_media" -> listOf(t("map",1,0,7,9),t("calendar",8,0,4,4),
                t("qr",8,4,4,5),t("media",1,9,11,3))
            "bottom_qr" -> listOf(t("map",1,0,7,8),t("calendar",8,0,4,4),
                t("media",8,4,4,4),t("qr",1,8,11,4))
            else -> listOf(map,t("calendar",7,0,5,2),
                t("qr",7,2,5,8),t("media",7,10,5,2))
        }
    }

    fun parse(raw: String?): List<Tile> {
        if (raw.isNullOrBlank()) return preset("vf_e34")
        return try {
            val obj = JSONObject(raw)
            val id = obj.optString("layoutPresetId", "vf_e34")
            if (obj.optBoolean("useCustomLayout", false)) {
                val items = obj.optJSONArray("customLayout") ?: return preset(id)
                val tiles = (0 until items.length()).map { i ->
                    val item = items.getJSONObject(i)
                    t(item.getString("panel"), item.getInt("x"), item.getInt("y"),
                        item.getInt("width"), item.getInt("height"))
                }
                if (valid(tiles)) tiles else preset(id)
            } else preset(id)
        } catch (_: Exception) { preset("vf_e34") }
    }

    private fun valid(tiles: List<Tile>): Boolean {
        val names = setOf("icons", "map", "calendar", "qr", "media")
        if (tiles.size != 5 || tiles.map { it.name }.toSet() != names) return false
        if (tiles.any { it.x < 0 || it.y < 0 || it.w < 1 || it.h < 1 ||
                it.x + it.w > 12 || it.y + it.h > 12 }) return false
        if (tiles.first { it.name == "icons" } != t("icons",0,0,1,12)) return false
        if (tiles.any { it.name != "icons" && it.x < 1 }) return false
        if (tiles.any { it.name == "map" && (it.w < 4 || it.h < 5) }) return false
        if (tiles.any { it.name != "map" && it.name != "icons" &&
                (it.w < 2 || it.h < 2) }) return false
        for (i in tiles.indices) for (j in i+1 until tiles.size) {
            val a = tiles[i]; val b = tiles[j]
            if (a.x < b.x+b.w && a.x+a.w > b.x &&
                a.y < b.y+b.h && a.y+a.h > b.y) return false
        }
        return true
    }

    fun read(context: Context): List<Tile> {
        val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        return parse(prefs.getString("flutter.car_layout_config_v1", null))
    }

    fun bounds(tile: Tile, safe: Rect): Rect {
        val x1 = safe.left + tile.x * safe.width() / 12
        val y1 = safe.top + tile.y * safe.height() / 12
        val x2 = safe.left + (tile.x + tile.w) * safe.width() / 12
        val y2 = safe.top + (tile.y + tile.h) * safe.height() / 12
        return Rect(x1, y1, x2, y2)
    }
}
