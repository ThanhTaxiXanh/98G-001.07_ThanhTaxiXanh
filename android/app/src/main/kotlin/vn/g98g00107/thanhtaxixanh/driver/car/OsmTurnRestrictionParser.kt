package vn.g98g00107.thanhtaxixanh.driver.car

import org.json.JSONObject

/**
 * M5.3T: strict, bounded OSM via-node restriction relation parser.
 * Input must be a FeatureCollection whose feature properties carry explicit
 * relation/way/node IDs. This is a review interchange, NOT generic Overpass JSON.
 */
object OsmTurnRestrictionParser {
    data class Relation(
        val relationId: String, val fromWay: String, val toWay: String,
        val viaNode: String, val restriction: String, val source: String
    )
    data class Result(val relations: List<Relation>, val rejected: Int)

    private fun id(raw: Any?): String? {
        val s = raw?.toString()?.trim() ?: return null
        return s.takeIf { it.matches(Regex("[1-9][0-9]{0,18}")) }
    }

    fun parse(json: String, source: String): Result {
        require(json.length <= 8_000_000)
        require(source.isNotBlank() && source.length <= 256)
        val root = JSONObject(json)
        require(root.optString("type") == "FeatureCollection")
        val features = root.getJSONArray("features")
        require(features.length() <= 20_000)
        val relations = ArrayList<Relation>()
        val seen = HashSet<String>()
        var rejected = 0
        for (i in 0 until features.length()) {
            val p = features.optJSONObject(i)?.optJSONObject("properties")
            val relation = id(p?.opt("osm_relation_id"))
            val from = id(p?.opt("from_osm_way_id"))
            val to = id(p?.opt("to_osm_way_id"))
            val via = id(p?.opt("via_osm_node_id"))
            val kind = p?.optString("restriction") ?: ""
            val validKind = kind.startsWith("no_") || kind.startsWith("only_")
            if (p?.optString("type") != "restriction" || relation == null ||
                from == null || to == null || via == null ||
                !validKind || !kind.matches(Regex("[a-z_]{4,48}")) ||
                !seen.add(relation)) {
                rejected++
                continue
            }
            relations.add(Relation(relation,from,to,via,kind,source))
        }
        return Result(relations,rejected)
    }
}
