package vn.g98g00107.thanhtaxixanh.driver.car

import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CarGrid12LayoutTest {
    @Test fun defaultPresetContainsAllPanels() {
        val tiles = CarGrid12Layout.parse(null)
        assertEquals(setOf("icons","map","calendar","qr","media"),
            tiles.map { it.name }.toSet())
    }

    @Test fun selectedPresetChangesGeometry() {
        val stacked = CarGrid12Layout.parse(
            """{"schemaVersion":2,"layoutPresetId":"stacked","useCustomLayout":false}""")
        val qrFirst = CarGrid12Layout.parse(
            """{"schemaVersion":2,"layoutPresetId":"qr_first","useCustomLayout":false}""")
        assertNotEquals(stacked.first { it.name == "qr" }.y,
            qrFirst.first { it.name == "qr" }.y)
    }

    @Test fun validCustomGridIsUsed() {
        val raw = """{"schemaVersion":2,"useCustomLayout":true,"customLayout":[
          {"panel":"icons","x":0,"y":0,"width":1,"height":12},
          {"panel":"map","x":1,"y":0,"width":6,"height":12},
          {"panel":"calendar","x":7,"y":0,"width":5,"height":4},
          {"panel":"qr","x":7,"y":4,"width":5,"height":4},
          {"panel":"media","x":7,"y":8,"width":5,"height":4}]}"""
        assertEquals(4,CarGrid12Layout.parse(raw).first { it.name == "qr" }.y)
    }

    @Test fun invalidOverlapFallsBackToPreset() {
        val raw = """{"schemaVersion":2,"useCustomLayout":true,"customLayout":[
          {"panel":"icons","x":0,"y":0,"width":1,"height":12},
          {"panel":"map","x":1,"y":0,"width":6,"height":12},
          {"panel":"calendar","x":7,"y":0,"width":5,"height":4},
          {"panel":"qr","x":7,"y":0,"width":5,"height":4},
          {"panel":"media","x":7,"y":8,"width":5,"height":4}]}"""
        assertEquals(2,CarGrid12Layout.parse(raw).first { it.name == "qr" }.y)
    }

    @Test fun scalesIntoHostSafeArea() {
        val rect = CarGrid12Layout.bounds(CarGrid12Layout.Tile("qr",7,2,5,8),
            Rect(10,20,1210,620))
        assertEquals(Rect(710,120,1210,520),rect)
    }
}
