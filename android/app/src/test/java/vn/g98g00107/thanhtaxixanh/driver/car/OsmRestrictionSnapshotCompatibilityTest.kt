package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test

class OsmRestrictionSnapshotCompatibilityTest {
    @Test fun noSnapshotRejected() {
        val r = OsmRestrictionSnapshotCompatibility.evaluate(setOf("OSM:A"),emptySet(),0)
        assertFalse(r.compatibleForReview)
        assertEquals("NO_RESTRICTION_SNAPSHOT",r.reason)
    }
    @Test fun mismatchedLabelsRejected() {
        val r = OsmRestrictionSnapshotCompatibility.evaluate(setOf("OSM:A"),setOf("OSM:B"),1)
        assertFalse(r.compatibleForReview)
        assertEquals("SOURCE_LABEL_MISMATCH",r.reason)
    }
    @Test fun matchingLabelsAreNotProofOfIdenticalExtract() {
        val r = OsmRestrictionSnapshotCompatibility.evaluate(setOf("OSM:A"),setOf("OSM:A"),1)
        assertFalse(r.compatibleForReview)
        assertEquals("MATCHING_LABEL_NOT_VERIFIED_EXTRACT",r.reason)
    }
    @Test fun mixedTopologySourcesRejected() {
        val r = OsmRestrictionSnapshotCompatibility.evaluate(
            setOf("OSM:A","OSM:B"),setOf("OSM:A"),1)
        assertEquals("MISSING_OR_MIXED_PROVENANCE",r.reason)
    }
}
