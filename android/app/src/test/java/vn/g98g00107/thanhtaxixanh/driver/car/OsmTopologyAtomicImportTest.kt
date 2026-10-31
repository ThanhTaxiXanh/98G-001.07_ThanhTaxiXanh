package vn.g98g00107.thanhtaxixanh.driver.car

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OsmTopologyAtomicImportTest {
    private lateinit var context: Context
    private val valid = """{"type":"FeatureCollection","features":[{
      "type":"Feature","properties":{"maxspeed":"60","osm_way_id":"700",
      "osm_node_ids":["11","12","13"],"oneway":"yes"},
      "geometry":{"type":"LineString","coordinates":[[106,21],[106.001,21],[106.002,21]]}
    }]}"""
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        OsmRoadSqliteStore.clear(context)
    }

    @Test fun importsRoadsAndLinksTogether() {
        val summary = OsmRoadSqliteStore.importTopologyGeoJson(context,valid,"OSM export")
        assertEquals(2,summary.segments)
        val graph = OsmRoadSqliteStore.topologyGraph(context)
        assertEquals(2,graph.edges.size)
        assertEquals("11",graph.edges[0].fromNode)
        assertEquals("13",graph.edges[1].toNode)
    }

    @Test fun rejectsDuplicateGeometryWithoutOverwritingOldData() {
        OsmRoadSqliteStore.importTopologyGeoJson(context,valid,"OSM export")
        val duplicate = """{"type":"FeatureCollection","features":[
          ${valid.substringAfter("\"features\":[").substringBeforeLast("]}")},
          ${valid.substringAfter("\"features\":[").substringBeforeLast("]}")}
        ]}"""
        try {
            OsmRoadSqliteStore.importTopologyGeoJson(context,duplicate,"OSM export")
            fail("Expected duplicate to abort transaction")
        } catch (_: IllegalArgumentException) {}
        assertEquals(2,OsmRoadSqliteStore.topologyGraph(context).edges.size)
    }

    @Test fun rejectsMissingNodesWithoutReplacingExistingData() {
        OsmRoadSqliteStore.importTopologyGeoJson(context,valid,"OSM export")
        val invalid = valid.replace("\"osm_node_ids\":[\"11\",\"12\",\"13\"],","")
        try {
            OsmRoadSqliteStore.importTopologyGeoJson(context,invalid,"OSM export")
            fail("Expected invalid source")
        } catch (_: IllegalArgumentException) {}
        assertEquals(2,OsmRoadSqliteStore.topologyGraph(context).edges.size)
    }
}
