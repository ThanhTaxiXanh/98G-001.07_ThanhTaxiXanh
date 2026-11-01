package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test

class OsmRoadFingerprintTest {
    private fun segment(kmh: Int = 60) =
        OsmRoadSegment(21.0, 106.0, 21.001, 106.0,
            kmh, null, null, 0, "Đường kiểm thử")

    @Test fun identicalSegmentsAcrossFilesMatch() {
        assertEquals(OsmRoadFingerprint.sha256(segment()),
            OsmRoadFingerprint.sha256(segment()))
    }

    @Test fun endpointReversalWithReversedDirectionIsEquivalent() {
        val a = OsmRoadSegment(21.0, 106.0, 21.001, 106.0,
            60, 80, 50, 1, "QL 1")
        val b = OsmRoadSegment(21.001, 106.0, 21.0, 106.0,
            60, 50, 80, -1, "QL 1")
        assertEquals(OsmRoadFingerprint.sha256(a),
            OsmRoadFingerprint.sha256(b))
    }

    @Test fun conflictingSpeedIsNotSilentlyDiscarded() {
        assertNotEquals(OsmRoadFingerprint.sha256(segment(60)),
            OsmRoadFingerprint.sha256(segment(40)))
    }

    @Test fun differentDirectionsWithDifferentEffectiveLimitsMustRemain() {
        val a = OsmRoadSegment(21.0, 106.0, 21.001, 106.0,
            null, 60, 40, 0, "")
        val b = a.copy(forwardKmh = 40, backwardKmh = 60)
        assertNotEquals(OsmRoadFingerprint.sha256(a),
            OsmRoadFingerprint.sha256(b))
    }

    @Test fun coordinatesQuantizeToSubMeterResolution() {
        val a = segment()
        val b = a.copy(lat1 = a.lat1 + 0.000000001)
        assertEquals(OsmRoadFingerprint.sha256(a),
            OsmRoadFingerprint.sha256(b))
    }
}
