package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test

class OsmTopologyParserTest {
    private fun geo(props: String, coordinates: String = "[[106.0,21.0],[106.001,21.0]]") =
        """{"type":"FeatureCollection","features":[{"type":"Feature","id":999,
        "properties":{$props},"geometry":{"type":"LineString","coordinates":$coordinates}}]}"""

    @Test fun acceptsExplicitWayAndNodeIdentifiers() {
        val parsed = OsmTopologyParser.parse(geo(
            "\"osm_way_id\":\"12345\",\"osm_node_ids\":[\"77\",\"78\"],\"layer\":\"1\",\"bridge\":\"yes\""),
            "OSM extract")
        assertEquals(1, parsed.links.size)
        assertEquals("12345", parsed.links.single().wayId)
        assertEquals("77", parsed.links.single().fromNode)
        assertEquals("78", parsed.links.single().toNode)
        assertEquals(1, parsed.links.single().layer)
        assertTrue(parsed.links.single().bridge)
    }

    @Test fun featureIdIsNotAssumedToBeOsmWayId() {
        val parsed = OsmTopologyParser.parse(geo("\"osm_node_ids\":[\"77\",\"78\"]"), "OSM")
        assertTrue(parsed.links.isEmpty())
        assertEquals(1, parsed.rejectedFeatures)
    }

    @Test fun mismatchedNodeCountRejected() {
        val parsed = OsmTopologyParser.parse(geo(
            "\"osm_way_id\":\"123\",\"osm_node_ids\":[\"77\"]"), "OSM")
        assertEquals(1, parsed.rejectedFeatures)
    }

    @Test fun malformedIdentifiersRejected() {
        val parsed = OsmTopologyParser.parse(geo(
            "\"osm_way_id\":\"way/123\",\"osm_node_ids\":[\"77\",\"78\"]"), "OSM")
        assertTrue(parsed.links.isEmpty())
    }

    @Test fun splitsEveryGeometryEdge() {
        val parsed = OsmTopologyParser.parse(geo(
            "\"osm_way_id\":\"123\",\"osm_node_ids\":[\"77\",\"78\",\"79\"]",
            "[[106,21],[106.001,21],[106.002,21]]"), "OSM")
        assertEquals(2, parsed.links.size)
        assertEquals("79", parsed.links.last().toNode)
    }
}
