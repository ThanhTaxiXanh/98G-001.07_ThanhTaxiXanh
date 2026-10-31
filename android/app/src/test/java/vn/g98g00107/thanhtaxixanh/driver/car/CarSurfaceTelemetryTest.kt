package vn.g98g00107.thanhtaxixanh.driver.car

import android.content.Context
import android.graphics.Rect
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CarSurfaceTelemetryTest {
    private lateinit var context: Context

    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        CarSurfaceTelemetry.clear(context)
    }

    private fun report(): JSONObject = JSONObject(CarSurfaceTelemetry.snapshot(context))

    @Test fun startsEmptyWithoutInventingSurfaceDimensions() {
        val r = report()
        assertEquals(0, r.getLong("eventCount"))
        assertFalse(r.getBoolean("lastObservedSurfaceActive"))
        assertFalse(r.has("surfaceWidth"))
    }

    @Test fun remembersHostRectanglesWithoutGpsOrQr() {
        CarSurfaceTelemetry.record(context, "surfaceAvailable", 1000, 600, null, null)
        CarSurfaceTelemetry.record(context, "visibleAreaChanged", 1000, 600,
            Rect(30, 0, 950, 550), null)
        CarSurfaceTelemetry.record(context, "stableAreaChanged", 1000, 600,
            Rect(30, 0, 950, 550), Rect(75, 25, 900, 540))
        val r = report()
        assertEquals(1000, r.getInt("surfaceWidth"))
        assertEquals(600, r.getInt("surfaceHeight"))
        assertEquals(75, r.getJSONObject("safe").getInt("left"))
        assertEquals(25, r.getJSONObject("safe").getInt("top"))
        assertTrue(r.getBoolean("overlaySafe"))
        assertEquals(1, r.getInt("surfacesCreated"))
        assertEquals(1, r.getInt("stableChanges"))
        assertFalse(r.toString().contains("latitude"))
        assertFalse(r.toString().contains("account"))
    }

    @Test fun tracksDetachWithoutClaimingLiveConnection() {
        CarSurfaceTelemetry.record(context, "surfaceAvailable", 800, 480, null, null)
        CarSurfaceTelemetry.record(context, "surfaceDestroyed", 800, 480, null, null)
        assertFalse(report().getBoolean("lastObservedSurfaceActive"))
        assertEquals(1, report().getInt("surfacesDestroyed"))
    }

    @Test fun boundsRecentEventsTo40AndSupportsExplicitClear() {
        repeat(80) {
            CarSurfaceTelemetry.record(context, "scroll", 934, 844,
                Rect(20, 20, 900, 810), null, "direction changed")
        }
        assertEquals(40, report().getJSONArray("events").length())
        assertEquals(80, report().getInt("gestureEvents"))
        CarSurfaceTelemetry.clear(context)
        assertEquals(0, report().getInt("eventCount"))
    }
}
