package vn.g98g00107.thanhtaxixanh.driver.car

import android.graphics.Rect

/**
 * Geometry reported by the Android Auto host; never infer it from the phone
 * resolution or from a 12x12 phone preview.
 */
data class CarSafeViewport(
    val bounds: Rect,
    val visibleArea: Rect?,
    val stableArea: Rect?,
    val overlaySafe: Boolean,
    val warning: String?
)

object CarSurfaceViewport {
    fun compute(width: Int, height: Int, visible: Rect?, stable: Rect?): CarSafeViewport {
        if (width <= 0 || height <= 0) {
            return CarSafeViewport(Rect(), null, null, false, "Invalid host surface")
        }
        val full = Rect(0, 0, width, height)

        fun clip(value: Rect?): Rect? {
            if (value == null || value.isEmpty) return null
            val clipped = Rect(value)
            if (!clipped.intersect(full) || clipped.isEmpty) return null
            return clipped
        }

        val v = clip(visible)
        val s = clip(stable)
        // Never draw persistent controls outside any known host inset.
        if (v != null && s != null) {
            val common = Rect(v)
            if (!common.intersect(s) || common.isEmpty) {
                return CarSafeViewport(Rect(), v, s, false,
                    "Visible and stable host areas do not overlap")
            }
            return CarSafeViewport(common, v, s, true, null)
        }

        // A missing/empty rectangle means UNKNOWN, not full visibility.
        val known = v ?: s
        return if (known != null) {
            CarSafeViewport(Rect(known), v, s, true,
                "Only one host inset is known; avoid unsupported overlays")
        } else {
            CarSafeViewport(Rect(full), null, null, false,
                "Host insets unknown; HUD hidden until host supplies them")
        }
    }

    fun toJson(rect: Rect?): Map<String, Int>? = rect?.let {
        mapOf("left" to it.left, "top" to it.top,
            "right" to it.right, "bottom" to it.bottom,
            "width" to it.width(), "height" to it.height())
    }
}
