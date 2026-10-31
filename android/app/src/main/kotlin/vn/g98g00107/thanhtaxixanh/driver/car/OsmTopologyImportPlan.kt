package vn.g98g00107.thanhtaxixanh.driver.car

import org.json.JSONArray
import org.json.JSONObject

/**
 * M5.3I: derive ordered road/topology pairs from the SAME GeoJSON features.
 * Fail closed when a maxspeed road cannot be paired one-to-one with explicit OSM nodes.
 * This is a staging parser; it does not write SQLite or activate warnings.
 */
object OsmTopologyImportPlan {
    data class Plan(
        val entries: List<Pair<OsmRoadSegment, OsmTopologyParser.Link>>,
        val skippedNonSpeedFeatures: Int
    )

    fun prepare(geoJson: String, source: String): Plan {
        require(geoJson.toByteArray(Charsets.UTF_8).size <= OsmRoadCatalog.MAX_BYTES)
        val root = JSONObject(geoJson)
        require(root.optString("type") == "FeatureCollection")
        val features = root.getJSONArray("features")
        require(features.length() <= 100_000)
        val pairs = ArrayList<Pair<OsmRoadSegment, OsmTopologyParser.Link>>()
        var skipped = 0
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i)
                ?: throw IllegalArgumentException("Malformed feature at index $i")
            val single = JSONObject().put("type", "FeatureCollection")
                .put("features", JSONArray().put(feature)).toString()
            val roads = OsmRoadCatalog.parse(single).roads
            if (roads.isEmpty()) {
                skipped++
                continue
            }
            val topology = OsmTopologyParser.parse(single, source)
            require(topology.rejectedFeatures == 0 && topology.links.size == roads.size) {
                "OSM topology missing or incompatible with speed geometry at feature $i"
            }
            for (j in roads.indices) pairs.add(roads[j] to topology.links[j])
            require(pairs.size <= OsmRoadCatalog.MAX_SEGMENTS)
        }
        require(pairs.isNotEmpty()) { "No roads with explicit OSM topology" }
        return Plan(pairs, skipped)
    }
}
