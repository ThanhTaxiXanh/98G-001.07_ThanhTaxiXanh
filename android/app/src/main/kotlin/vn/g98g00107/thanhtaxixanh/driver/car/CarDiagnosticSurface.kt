package vn.g98g00107.thanhtaxixanh.driver.car

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer

/**
 * Feasibility-only surface. This is explicitly NOT an OSM map or full dashboard.
 * Reports the actual host-provided surface dimensions for device testing.
 */
class CarDiagnosticSurface : SurfaceCallback {
    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        Log.i("ThanhTaxiCar", "CAR_SURFACE ${surfaceContainer.width}x${surfaceContainer.height} px")
        val surface = surfaceContainer.surface ?: return
        if (!surface.isValid) return
        val canvas = try { surface.lockCanvas(null) } catch (e: Exception) {
            Log.e("ThanhTaxiCar", "Unable to draw surface", e)
            return
        }
        try {
            canvas.drawColor(Color.rgb(7, 20, 35))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 196, 0)
                textSize = 33f
            }
            canvas.drawText("ThanhTaxiXanh - Surface test", 24f, 60f, paint)
            paint.color = Color.WHITE
            paint.textSize = 23f
            canvas.drawText("Chua co ban do OSM - M0", 24f, 102f, paint)
            canvas.drawText("${surfaceContainer.width} x ${surfaceContainer.height} px",
                24f, 144f, paint)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }
    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.i("ThanhTaxiCar", "Surface destroyed")
    }
    override fun onVisibleAreaChanged(visibleArea: Rect) {
        Log.i("ThanhTaxiCar", "visible area: $visibleArea")
    }
    override fun onStableAreaChanged(stableArea: Rect) {
        Log.i("ThanhTaxiCar", "stable area: $stableArea")
    }
}
