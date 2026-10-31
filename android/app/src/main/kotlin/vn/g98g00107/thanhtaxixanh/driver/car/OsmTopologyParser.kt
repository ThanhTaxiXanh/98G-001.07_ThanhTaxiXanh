package vn.g98g00107.thanhtaxixanh.driver.car

import org.json.JSONObject

/**
 * M5.3D provenance validator for OSM road topology GeoJSON.
 * Only explicitly supplied OSM way and ordered OSM node identifiers are accepted.
 * A GeoJSON feature.id alone is not assumed to be an OSM way ID.
 */
object OsmTopologyParser {
    data class Link(
        val wayId: String,
        val fromNode: String,
        val toNode: String,
        val layer: Int?,
        val bridge: Boolean,
        val tunnel: Boolean,
        val source: String
    )
    data class Result(val links: List<Link>, val rejectedFeatures: Int)

    private fun osmId(value: Any?): String? {
        val s = value?.toString()?.trim() ?: return null
        return s.takeIf { it.matches(Regex("[1-9][0-9]{0,18}")) }
    }

    /** Requires explicit ordered node IDs matching the geometry vertex count. */
    fun parse(geoJson: String, source: String): Result {
        require(source.isNotBlank() && source.length <= 256)
        val root = JSONObject(geoJson)
        require(root.optString("type") == "FeatureCollection")
        val features = root.getJSONArray("features")
        require(features.length() <= 100_000)
        val result = ArrayList<Link>()
        var rejected = 0
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i)
            val props = feature?.optJSONObject("properties")
            val geometry = feature?.optJSONObject("geometry")
            val wayId = osmId(props?.opt("osm_way_id"))
            val coordinates = geometry?.optJSONArray("coordinates")
            val nodes = props?.optJSONArray("osm_node_ids")
            if (wayId == null || geometry?.optString("type") != "LineString" ||
                coordinates == null || nodes == null ||
                nodes.length() != coordinates.length() || nodes.length() < 2) {
                rejected++
                continue
            }
            val nodeIds = (0 until nodes.length()).map { osmId(nodes.opt(it)) }
            val coordsValid = (0 until coordinates.length()).all { j ->
                val point = coordinates.optJSONArray(j)
                if (point == null || point.length() < 2) false else {
                    val lon = point.optDouble(0, Double.NaN)
                    val lat = point.optDouble(1, Double.NaN)
                    lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 &&
                        lon in -180.0..180.0
                }
            }
            if (nodeIds.any { it == null } || !coordsValid ||
                (0 until nodeIds.size - 1).any { nodeIds[it] == nodeIds[it + 1] }) {
                rejected++
                continue
            }
            val layer = if (props.has("layer")) {
                props.optString("layer").toIntOrNull()?.takeIf { it in -10..10 }
            } else null
            if (props.has("layer") && layer == null) {
                rejected++
                continue
            }
            val bridge = props.optString("bridge").lowercase() in setOf("yes", "true", "1")
            val tunnel = props.optString("tunnel").lowercase() in setOf("yes", "true", "1")
            for (j in 1 until nodeIds.size) {
                result.add(Link(wayId, nodeIds[j - 1]!!, nodeIds[j]!!,
                    layer, bridge, tunnel, source))
                require(result.size <= 50_000) { "Too many topology segments" }
            }
        }
        return Result(result, rejected)
    }
}
