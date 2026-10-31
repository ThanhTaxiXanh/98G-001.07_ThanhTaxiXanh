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
class OsmTopologyGraphStoreTest {
    private lateinit var context: Context
    private val a = OsmRoadSegment(21.0,106.0,21.0,106.001,60,null,null,1,"A")
    private val b = OsmRoadSegment(21.0,106.001,21.0,106.002,60,null,null,1,"B")
    private val la = OsmTopologyParser.Link("100","10","11",null,false,false,"OSM")
    private val lb = OsmTopologyParser.Link("101","11","12",null,false,false,"OSM")

    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        OsmRoadSqliteStore.clear(context)
    }

    @Test fun unlinkedLegacyRoadsAreExcluded() {
        OsmRoadSqliteStore.replace(context,listOf(a),1)
        assertTrue(OsmRoadSqliteStore.topologyGraph(context).edges.isEmpty())
    }

    @Test fun linkedRoadsProduceConnectedDirectedGraph() {
        OsmRoadSqliteStore.replace(context,listOf(a,b),2)
        assertEquals(2,OsmRoadSqliteStore.attachTopology(context,
            listOf(a to la,b to lb)))
        val graph = OsmRoadSqliteStore.topologyGraph(context)
        assertEquals(2,graph.edges.size)
        assertTrue(DirectedRoadGraph.transition(graph.edges,emptySet(),
            graph.edges[0].id,graph.edges[1].id).allowed)
    }

    @Test fun refusesTruncatedTopology() {
        OsmRoadSqliteStore.replace(context,listOf(a,b),2)
        OsmRoadSqliteStore.attachTopology(context,listOf(a to la,b to lb))
        try {
            OsmRoadSqliteStore.topologyGraph(context,1)
            fail("Must refuse incomplete graph")
        } catch (_: IllegalArgumentException) {}
    }
}
