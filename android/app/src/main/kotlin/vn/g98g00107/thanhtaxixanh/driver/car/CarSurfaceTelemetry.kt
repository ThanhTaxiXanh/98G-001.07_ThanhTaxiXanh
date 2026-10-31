package vn.g98g00107.thanhtaxixanh.driver.car

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/**
 * A small diagnostic diary stored on the PHONE.
 * Does NOT persist GPS latitude, longitude, bank data, QR, or personal events.
 * Last observed host state is not proof the car is currently connected.
 */
object CarSurfaceTelemetry {
    private const val PREFS = "car_host_diagnostics_v1"
    private const val KEY = "last_report"
    private const val MAX_EVENTS = 40
    private var lastFrameWriteAtElapsed = 0L
    private var pendingFrames = 0L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(context: Context): JSONObject {
        val raw = prefs(context).getString(KEY, null)
        return try {
            if (raw == null) JSONObject() else JSONObject(raw)
        } catch (_: Exception) { JSONObject() }
    }

    private fun objectOrNull(rect: Rect?): JSONObject? {
        val map = CarSurfaceViewport.toJson(rect) ?: return null
        return JSONObject().apply { for ((k, v) in map) put(k, v) }
    }

    @Synchronized
    fun record(context: Context, event: String, width: Int, height: Int,
               visible: Rect?, stable: Rect?, extra: String? = null,
               dpi: Int? = null) {
        val report = read(context)
        val now = System.currentTimeMillis()
        report.put("version", 1)
        report.put("lastObservedAt", now)
        report.put("lastEvent", event)
        // Preserve the last REAL host geometry after Screen is destroyed:
        // a sessionStopped callback has no Surface and must not replace it
        // with a fictional 0x0 screen.
        if (width > 0 && height > 0) {
            val safe = CarSurfaceViewport.compute(width, height, visible, stable)
            report.put("surfaceWidth", width)
            report.put("surfaceHeight", height)
            report.put("surfaceAspectRatio", width.toDouble() / height)
            report.put("visible", objectOrNull(safe.visibleArea) ?: JSONObject.NULL)
            report.put("stable", objectOrNull(safe.stableArea) ?: JSONObject.NULL)
            report.put("safe", objectOrNull(safe.bounds) ?: JSONObject.NULL)
            report.put("overlaySafe", safe.overlaySafe)
            report.put("warning", safe.warning ?: JSONObject.NULL)
        }
        if (dpi != null) report.put("surfaceDpi", dpi.coerceAtLeast(0))
        report.put("lastFrameAt", report.optLong("lastFrameAt", 0L))
        val eventCount = report.optLong("eventCount", 0L) + 1L
        report.put("eventCount", eventCount)

        val active = when (event) {
            "surfaceAvailable" -> true
            "surfaceDestroyed", "sessionStopped" -> false
            else -> report.optBoolean("lastObservedSurfaceActive", false)
        }
        report.put("lastObservedSurfaceActive", active)
        val key = when (event) {
            "surfaceAvailable" -> "surfacesCreated"
            "surfaceDestroyed" -> "surfacesDestroyed"
            "visibleAreaChanged" -> "visibleChanges"
            "stableAreaChanged" -> "stableChanges"
            "scale", "scroll", "fling", "click" -> "gestureEvents"
            else -> null
        }
        if (key != null) {
            report.put(key, report.optLong(key, 0L) + 1L)
        }

        val recent = report.optJSONArray("events") ?: JSONArray()
        val entry = JSONObject().put("at", now).put("event", event)
        if (!extra.isNullOrBlank()) entry.put("details", extra.take(160))
        recent.put(entry)
        val clipped = JSONArray()
        for (i in maxOf(0, recent.length() - MAX_EVENTS) until recent.length()) {
            clipped.put(recent.get(i))
        }
        report.put("events", clipped)
        prefs(context).edit().putString(KEY, report.toString()).apply()
    }

    /**
     * Throttle disk writes while recording painted frames (max every ~5s).
     * A frame counter is diagnostic only, not a measured live frame rate.
     */
    @Synchronized
    fun framePosted(context: Context) {
        pendingFrames++
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameWriteAtElapsed < 5_000L) return
        val report = read(context)
        report.put("lastFrameAt", System.currentTimeMillis())
        report.put("framesRecorded", report.optLong("framesRecorded", 0L) + pendingFrames)
        pendingFrames = 0L
        lastFrameWriteAtElapsed = now
        prefs(context).edit().putString(KEY, report.toString()).apply()
    }

    @Synchronized
    fun snapshot(context: Context): String = read(context).apply {
        if (!has("version")) {
            put("version", 1)
            put("eventCount", 0)
            put("lastObservedSurfaceActive", false)
            put("message", "Chưa ghi nhận kết nối Android Auto của ứng dụng")
            put("events", JSONArray())
        }
    }.toString()

    @Synchronized
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).commit()
        pendingFrames = 0L
        lastFrameWriteAtElapsed = 0L
    }
}
