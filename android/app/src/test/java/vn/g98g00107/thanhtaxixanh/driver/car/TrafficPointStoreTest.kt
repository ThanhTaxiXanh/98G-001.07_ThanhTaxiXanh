package vn.g98g00107.thanhtaxixanh.driver.car

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TrafficPointStoreTest {
    private lateinit var context: Context

    @Before fun reset() {
        context = org.robolectric.RuntimeEnvironment.getApplication()
        context.deleteDatabase("traffic_points_m5.sqlite")
    }

    private fun point(kind: String = "speed_limit", speed: String = """"speedKmh":50,""",
                      extra: String = "") = """
        {"type":"Feature","geometry":{"type":"Point","coordinates":[106.2083,21.2815]},
         "properties":{"kind":"$kind",$speed"source":"OSM",
           "license":"ODbL-1.0","sourceRef":"node/123",
           "verification":"VERIFIED"$extra}}
    """.trimIndent()

    private fun fc(vararg features: String) =
        """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    @Test fun importsWithoutTrustingVerificationField() {
        val result = TrafficPointStore.importText(context, fc(point()))
        assertEquals(1, result["total"])
        assertEquals(1, result["unverified"])
        assertEquals(0, result["verified"])
        assertEquals(1, result["lastAdded"])
        assertEquals("speed_limit", TrafficPointStore.candidates(
            context, 21.0, 106.0, 22.0, 107.0).single().kind)
    }

    @Test fun duplicateAppendIsIdempotentAndReplaceIsAtomic() {
        TrafficPointStore.importText(context, fc(point()))
        val repeat = TrafficPointStore.importText(context, fc(point()), append = true)
        assertEquals(0, repeat["lastAdded"])
        assertEquals(1, repeat["lastSkipped"])
        assertEquals(1, repeat["total"])
        val replaced = TrafficPointStore.importText(context,
            fc(point(kind = "camera_speed", speed = "")), append = false)
        assertEquals(1, replaced["total"])
        assertEquals("camera_speed", TrafficPointStore.candidates(
            context, 21.0, 106.0, 22.0, 107.0).single().kind)
    }

    @Test fun invalidFileNeverDeletesPreviousImport() {
        TrafficPointStore.importText(context, fc(point()))
        try {
            TrafficPointStore.importText(context,
                fc(point(kind = "camera_speed", speed = "" ),
                    point(kind = "wrong_kind", speed = "")), append = false)
            fail("should reject invalid kind")
        } catch (_: IllegalArgumentException) {}
        assertEquals(1, TrafficPointStore.status(context)["total"])
    }

    @Test fun requiresExactPointNotWayCentroidOrOutsideVietnam() {
        val wrongGeometry = point().replace("\"Point\"", "\"LineString\"")
        assertThrows(IllegalArgumentException::class.java) {
            TrafficPointStore.parse(fc(wrongGeometry))
        }
        val outside = point().replace("106.2083,21.2815", "99.0,21.2815")
        assertThrows(IllegalArgumentException::class.java) {
            TrafficPointStore.parse(fc(outside))
        }
    }

    @Test fun rejectsSpeedErrorsAndMissingProvenance() {
        assertThrows(IllegalArgumentException::class.java) {
            TrafficPointStore.parse(fc(point(speed = "")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TrafficPointStore.parse(fc(point(speed = """"speedKmh":250,""")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TrafficPointStore.parse(fc(point().replace("\"license\":\"ODbL-1.0\"",
                "\"license\":\"\"")))
        }
    }

    @Test fun bearingAndSourceCanChangeIdentityWithoutDisablingReview() {
        val original = point(extra = ""","bearing":90""")
        val alternate = point(extra = ""","bearing":270""")
        val status = TrafficPointStore.importText(context, fc(original, alternate))
        assertEquals(2, status["total"])
        assertEquals(0, status["verified"])
        val count = TrafficPointStore.candidates(context, 21.28, 106.20, 21.29, 106.22)
        assertEquals(setOf(90.0, 270.0), count.map { it.bearing }.toSet())
    }

    @Test fun clearOnlyAffectsTrafficPointStore() {
        TrafficPointStore.importText(context, fc(point()))
        assertEquals(0, TrafficPointStore.clear(context)["total"])
        assertTrue(TrafficPointStore.candidates(context, 21.0, 106.0, 22.0, 107.0).isEmpty())
    }
}
