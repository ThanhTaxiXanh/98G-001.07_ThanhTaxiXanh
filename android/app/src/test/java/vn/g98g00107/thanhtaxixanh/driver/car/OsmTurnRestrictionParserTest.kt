package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test

class OsmTurnRestrictionParserTest {
    private fun json(kind: String) = """{"type":"FeatureCollection","features":[
      {"type":"Feature","properties":{"type":"restriction","osm_relation_id":"9001",
      "from_osm_way_id":"100","to_osm_way_id":"200","via_osm_node_id":"300",
      "restriction":"$kind"}}]}"""

    @Test fun parsesExplicitNoTurnWithProvenance() {
        val result = OsmTurnRestrictionParser.parse(json("no_left_turn"),"OSM:test")
        assertEquals(1,result.relations.size)
        assertEquals("9001",result.relations[0].relationId)
        assertEquals("OSM:test",result.relations[0].source)
    }
    @Test fun rejectsUnrecognizedRestriction() {
        assertEquals(1,OsmTurnRestrictionParser.parse(json("left"),"OSM:test").rejected)
    }
    @Test fun resolvesExplicitNoTurn() {
        val relations = OsmTurnRestrictionParser.parse(json("no_left_turn"),"OSM:test").relations
        val edges = listOf(
            DirectedRoadGraph.Edge("1:f","A","300",100.0,"100"),
            DirectedRoadGraph.Edge("2:f","300","B",100.0,"200"))
        val result = OsmTurnRestrictionResolver.resolve(relations,edges)
        assertEquals(1,result.resolvedRelations)
        assertEquals(1,result.restrictions.size)
        assertEquals("UNVERIFIED_REVIEW_ONLY",result.status)
    }
    @Test fun onlyTurnRemainsUnresolved() {
        val relations = OsmTurnRestrictionParser.parse(json("only_right_turn"),"OSM:test").relations
        val edges = listOf(
            DirectedRoadGraph.Edge("1:f","A","300",100.0,"100"),
            DirectedRoadGraph.Edge("2:f","300","B",100.0,"200"))
        val result = OsmTurnRestrictionResolver.resolve(relations,edges)
        assertEquals(1,result.unresolvedRelations)
        assertTrue(result.restrictions.isEmpty())
    }
    @Test fun absentTopologyIsUnresolved() {
        val relations = OsmTurnRestrictionParser.parse(json("no_left_turn"),"OSM:test").relations
        assertEquals(1,OsmTurnRestrictionResolver.resolve(relations,emptyList()).unresolvedRelations)
    }
}
