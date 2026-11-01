package vn.g98g00107.thanhtaxixanh.driver.car

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GpxTraceParserTest {
    private fun gpx(points: String) = """<gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1" creator="test"><trk><trkseg>$points</trkseg></trk></gpx>"""
    private fun point(lat: String, lon: String, time: String) =
        """<trkpt lat="$lat" lon="$lon"><time>$time</time></trkpt>"""

    @Test fun parsesTimestampedTrackPoints() {
        val result = GpxTraceParser.parse(gpx(
            point("21.2","106.2","2026-10-25T10:00:00Z") +
            point("21.3","106.3","2026-10-25T10:00:01Z")))
        assertEquals(2,result.fixes.size)
        assertEquals(0,result.rejectedPoints)
        assertNull(result.fixes[0].heading)
        assertEquals(25.0,result.fixes[0].accuracyM,0.0)
    }

    @Test fun rejectsMissingTimeAndInvalidCoordinates() {
        val result = GpxTraceParser.parse(gpx(
            """<trkpt lat="21.2" lon="106.2"/>""" +
            point("95","106","2026-10-25T10:00:00Z")))
        assertEquals(0,result.fixes.size)
        assertEquals(2,result.rejectedPoints)
    }

    @Test fun rejectsOutOfOrderTimestamps() {
        val result = GpxTraceParser.parse(gpx(
            point("21","106","2026-10-25T10:00:02Z") +
            point("21","106.1","2026-10-25T10:00:01Z")))
        assertEquals(1,result.fixes.size)
        assertEquals(1,result.rejectedPoints)
    }

    @Test fun forbidsDoctype() {
        try {
            GpxTraceParser.parse("<!DOCTYPE gpx><gpx/>")
            fail("DOCTYPE must be forbidden")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun rejectsNonGpxXml() {
        try {
            GpxTraceParser.parse("<root/>")
            fail("Not GPX")
        } catch (_: IllegalArgumentException) {}
    }
}
