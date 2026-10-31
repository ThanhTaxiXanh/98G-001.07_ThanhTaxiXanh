package vn.g98g00107.thanhtaxixanh.driver.car

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OsmRestrictionSqliteStoreTest {
    private fun relation(id: String, source: String = "OSM:test") =
        OsmTurnRestrictionParser.Relation(id,"100","200","300","no_left_turn",source)

    @Test fun snapshotRoundTrip() {
        val context = RuntimeEnvironment.getApplication()
        val summary = OsmRestrictionSqliteStore.replace(context,listOf(relation("901")),"OSM:test")
        assertEquals(1,summary.count)
        assertEquals("901",OsmRestrictionSqliteStore.read(context).single().relationId)
    }
    @Test fun replacementRemovesOldSnapshot() {
        val context = RuntimeEnvironment.getApplication()
        OsmRestrictionSqliteStore.replace(context,listOf(relation("902")),"OSM:test")
        OsmRestrictionSqliteStore.replace(context,listOf(relation("903")),"OSM:test")
        assertEquals(listOf("903"),OsmRestrictionSqliteStore.read(context).map { it.relationId })
    }
    @Test fun mismatchedProvenanceCannotOverwrite() {
        val context = RuntimeEnvironment.getApplication()
        OsmRestrictionSqliteStore.replace(context,listOf(relation("904")),"OSM:test")
        try {
            OsmRestrictionSqliteStore.replace(context,listOf(relation("905","OSM:other")),"OSM:test")
            fail("Expected provenance rejection")
        } catch (_: IllegalArgumentException) {}
        assertEquals("904",OsmRestrictionSqliteStore.read(context).single().relationId)
    }
}
