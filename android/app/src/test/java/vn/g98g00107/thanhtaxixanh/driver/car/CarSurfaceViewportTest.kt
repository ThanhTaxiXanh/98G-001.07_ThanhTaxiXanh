package vn.g98g00107.thanhtaxixanh.driver.car

import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CarSurfaceViewportTest {
    @Test fun intersectVisibleAndStableToKeepHudOutOfChrome() {
        val safe = CarSurfaceViewport.compute(1000, 600,
            Rect(70, 20, 950, 570), Rect(100, 0, 900, 550))
        assertTrue(safe.overlaySafe)
        assertEquals(Rect(100, 20, 900, 550), safe.bounds)
    }
    @Test fun clippedRectsNeverEscapeSurface() {
        val safe = CarSurfaceViewport.compute(800, 480,
            Rect(-100, 18, 900, 600), Rect(24, -80, 1000, 420))
        assertTrue(safe.overlaySafe)
        assertEquals(Rect(24, 18, 800, 420), safe.bounds)
    }
    @Test fun unknownInsetsAreNotTreatedAsVerifiedFreeSpace() {
        val safe = CarSurfaceViewport.compute(934, 844, Rect(), null)
        assertFalse(safe.overlaySafe)
        assertEquals(Rect(0, 0, 934, 844), safe.bounds)
    }
    @Test fun disjointInsetsBlockOverlayRatherThanShowingFullScreen() {
        val safe = CarSurfaceViewport.compute(800, 480,
            Rect(0, 0, 200, 480), Rect(400, 0, 800, 480))
        assertFalse(safe.overlaySafe)
        assertTrue(safe.bounds.isEmpty)
    }
    @Test fun oneKnownInsetAllowsBoundedConservativeOverlay() {
        val safe = CarSurfaceViewport.compute(800, 480,
            Rect(20, 0, 700, 450), null)
        assertTrue(safe.overlaySafe)
        assertEquals(Rect(20, 0, 700, 450), safe.bounds)
        assertNotNull(safe.warning)
    }
    @Test fun geometryHandlesSurfaceRecreationAndInvalidSizes() {
        assertFalse(CarSurfaceViewport.compute(0, 480, null, null).overlaySafe)
        assertEquals(Rect(0, 0, 1280, 720),
            CarSurfaceViewport.compute(1280, 720, null, null).bounds)
    }
}
