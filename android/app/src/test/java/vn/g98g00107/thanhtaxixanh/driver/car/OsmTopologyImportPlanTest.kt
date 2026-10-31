package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OsmTopologyImportPlanTest {
    private fun feature(props: String) = """
        {"type":"Feature","properties":{$props},
         "geometry":{"type":"LineString","coordinates":[[106,21],[106.001,21],[106.002,21]]}}
    """.trimIndent()
    private fun collection(vararg features: String) =
        """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
    private val valid = feature(
        "\"maxspeed\":\"60\",\"osm_way_id\":\"500\",\"osm_node_ids\":[\"1\",\"2\",\"3\"]")

    @Test fun pairsEachSpeedSegmentWithItsOwnNodes() {
        val result = OsmTopologyImportPlan.prepare(collection(valid),"OSM export")
        assertEquals(2,result.entries.size)
        assertEquals("1",result.entries[0].second.fromNode)
        assertEquals("2",result.entries[0].second.toNode)
        assertEquals("2",result.entries[1].second.fromNode)
        assertEquals("3",result.entries[1].second.toNode)
        assertEquals(60,result.entries[0].first.generalKmh)
    }

    @Test fun skipsNonSpeedFeaturesWithoutReindexing() {
        val noSpeed = feature("\"name\":\"unrestricted\"")
        val result = OsmTopologyImportPlan.prepare(collection(noSpeed,valid),"OSM")
        assertEquals(1,result.skippedNonSpeedFeatures)
        assertEquals(2,result.entries.size)
    }

    @Test fun missingNodesRejectsEntirePlan() {
        val missing = feature("\"maxspeed\":\"50\",\"osm_way_id\":\"501\"")
        try {
            OsmTopologyImportPlan.prepare(collection(valid,missing),"OSM")
            fail("Should reject incomplete topology")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun multilineGeometryCannotBeSilentlyPaired() {
        val multi = """{"type":"Feature","properties":{
          "maxspeed":"60","osm_way_id":"500","osm_node_ids":["1","2"]},
          "geometry":{"type":"MultiLineString","coordinates":[[[106,21],[106.001,21]]]}}"""
        try {
            OsmTopologyImportPlan.prepare(collection(multi),"OSM")
            fail("Should reject ambiguous multiline topology")
        } catch (_: IllegalArgumentException) {}
    }
}
