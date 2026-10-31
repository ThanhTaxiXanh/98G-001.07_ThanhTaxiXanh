package vn.g98g00107.thanhtaxixanh.driver.car

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.Handler
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import android.util.Log
import android.view.Surface
import kotlin.math.ln
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.snapshotter.MapSnapshotter

/**
 * Independently written OSM map snapshot renderer for host-provided AA Surface.
 * No routing, offline basemap guarantee or authoritative speed-limit database yet.
 */
class CarOsmMapSurface(private val context: Context) : SurfaceCallback {
    private val tag = "ThanhTaxiCar"
    private val style = "https://tiles.openfreemap.org/styles/liberty"
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var targetSurface: Surface? = null
    private var width = 0
    private var height = 0
    private var stable: Rect? = null
    private var visible: Rect? = null
    private var snapshotter: MapSnapshotter? = null
    private var lastMap: Bitmap? = null
    private var generation = 0
    private var rendering = false
    private var dirty = false
    private var zoom = 5.4
    private var center = LatLng(16.0, 107.5) // Overview only, NOT a fake current position.
    private var hasFix = false
    private var lastFixAt = 0L
    private var speedKmh: Int? = null
    private var error: String? = null
    private var roadCount = 0
    private var lastLocation: Location? = null
    private var roadMatch: OsmSpeedMatch? = null
    private var worker: ExecutorService? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var matchSequence = 0
    private var roadDataError: String? = null


    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location.latitude !in -90.0..90.0 ||
                location.longitude !in -180.0..180.0) return
            val age = System.currentTimeMillis() - location.time
            if (age !in 0..120_000L) return
            center = LatLng(location.latitude, location.longitude)
            if (!hasFix) zoom = 16.2
            hasFix = true
            lastFixAt = location.time
            speedKmh = if (location.hasSpeed()) {
                (location.speed * 3.6f).toInt().coerceAtLeast(0)
            } else null
            matchRoadSpeed(location)
            requestMap()
        }
    }

    private fun locationGranted(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun beginLocation() {
        if (!locationGranted()) return
        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            providers.mapNotNull { provider ->
                if (locationManager.isProviderEnabled(provider))
                    locationManager.getLastKnownLocation(provider) else null
            }.filter { System.currentTimeMillis() - it.time in 0..120_000L }
                .maxByOrNull { it.time }?.let { locationListener.onLocationChanged(it) }
            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(
                        provider, 4000L, 10f, locationListener, Looper.getMainLooper())
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Location unavailable or permission revoked", e)
        }
    }

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        // The host may re-provide a Surface when size/DPI changes.
        // Release only a *different* previous instance; reuse is legitimate.
        if (targetSurface !== container.surface) targetSurface?.release()
        stable = null
        visible = null
        generation++
        snapshotter?.cancel()
        snapshotter = null
        rendering = false
        dirty = false
        lastMap = null
        worker?.shutdownNow()
        worker = Executors.newSingleThreadExecutor()
        roadCount = 0
        lastLocation = null
        roadMatch = null
        roadDataError = null
        targetSurface = container.surface
        width = container.width
        height = container.height
        CarSurfaceTelemetry.record(context, "surfaceAvailable", width, height,
            visible, stable, dpi = container.dpi)
        Log.i(tag, "OSM surface " + width + "x" + height)
        if (width < 32 || height < 32 || targetSurface?.isValid != true) return
        drawFrame()
        try {
            MapLibre.getInstance(context)
            snapshotter = MapSnapshotter(context,
                MapSnapshotter.Options(width, height)
                    .withStyle(style).withPixelRatio(1f)
                    .withCameraPosition(camera()))
            error = null
            beginLocation()
            loadRoadData()
            requestMap()
        } catch (e: Exception) {
            error = "Không thể khởi tạo bản đồ OSM"
            Log.e(tag, "MapLibre initialization failed", e)
            drawFrame()
        }
    }

    private fun loadRoadData() {
        val queue = worker ?: return
        val version = generation
        queue.execute {
            val result = runCatching {
                OsmRoadSqliteStore.migrateLegacy(context)
                OsmRoadSqliteStore.status(context)
            }
            mainHandler.post {
                if (version == generation) {
                    roadCount = (result.getOrNull()?.get("segments") as? Int) ?: 0
                    roadDataError = result.exceptionOrNull()?.message
                    Log.i(tag, "Indexed OSM road segments: " + roadCount)
                    lastLocation?.let { matchRoadSpeed(it) }
                    drawFrame()
                }
            }
        }
    }

    private fun matchRoadSpeed(location: Location) {
        roadMatch = null
        lastLocation = Location(location)
        val queue = worker ?: return
        if (roadCount == 0) return
        val accuracy = if (location.hasAccuracy()) location.accuracy.toDouble()
            else Double.POSITIVE_INFINITY
        val bearing = if (location.hasBearing() &&
            (!location.hasBearingAccuracy() ||
                location.bearingAccuracyDegrees <= 40f)) {
            location.bearing.toDouble()
        } else null
        val speed = if (location.hasSpeed()) location.speed.toDouble() else 0.0
        val lat = location.latitude
        val lon = location.longitude
        val request = ++matchSequence
        val version = generation
        queue.execute {
            val result = runCatching {
                val nearby = OsmRoadSqliteStore.nearby(context, lat, lon)
                nearby?.let { OsmRoadMatcher.match(it, lat, lon, accuracy, bearing, speed) }
            }
            mainHandler.post {
                if (version == generation && request == matchSequence) {
                    roadMatch = result.getOrNull()
                    roadDataError = result.exceptionOrNull()?.message
                    drawFrame()
                }
            }
        }
    }

    private fun camera(): CameraPosition =
        CameraPosition.Builder().target(center).zoom(zoom).build()

    fun zoomBy(delta: Double) {
        zoom = (zoom + delta).coerceIn(3.0, 19.0)
        requestMap()
    }

    private fun requestMap() {
        val map = snapshotter ?: return
        if (targetSurface?.isValid != true) return
        if (rendering) {
            dirty = true
            return
        }
        rendering = true
        val currentGeneration = generation
        try {
            map.setCameraPosition(camera())
            map.start({ result ->
                if (currentGeneration == generation) {
                    rendering = false
                    error = null
                    lastMap = result.bitmap
                    CarSurfaceTelemetry.record(context, "snapshotReady", width,
                        height, visible, stable)
                    drawFrame()
                    if (dirty) {
                        dirty = false
                        requestMap()
                    }
                }
            }, { failure ->
                if (currentGeneration == generation) {
                    rendering = false
                    error = "Không tải được lớp bản đồ OSM"
                    CarSurfaceTelemetry.record(context, "snapshotFailed", width,
                        height, visible, stable)
                    Log.w(tag, "Map snapshot failed: " + failure)
                    drawFrame()
                }
            })
        } catch (e: Exception) {
            rendering = false
            error = "Không thể cập nhật bản đồ"
            Log.w(tag, "Map snapshot exception", e)
            drawFrame()
        }
    }

    private fun safeViewport(): CarSafeViewport =
        CarSurfaceViewport.compute(width, height, visible, stable)

    private fun drawFrame() {
        val surface = targetSurface ?: return
        if (!surface.isValid) return
        var canvas: Canvas? = null
        try {
            canvas = surface.lockCanvas(null)
            canvas.drawColor(Color.rgb(9, 21, 33))
            lastMap?.takeIf { !it.isRecycled }?.let {
                canvas.drawBitmap(it, null, Rect(0, 0, width, height),
                    Paint(Paint.FILTER_BITMAP_FLAG))
            }
            val safeViewport = safeViewport()
            // Always render the map to the host Surface. If the host has not
            // provided reliable insets, do NOT put speed/HUD above host chrome.
            if (!safeViewport.overlaySafe || safeViewport.bounds.width() < 180 ||
                safeViewport.bounds.height() < 140) return
            val area = safeViewport.bounds
            val scale = (minOf(width, height) / 680f).coerceIn(0.60f, 1.35f)
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 23f * scale
            }
            val secondary = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 16f * scale
            }
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(218, 12, 25, 40)
            }
            val x = area.left + 12f
            val y = area.top + 12f
            canvas.drawRoundRect(x, y, x + minOf(area.width() - 24f, 360f * scale),
                y + 98f * scale, 12f, 12f, bg)
            val fresh = hasFix && System.currentTimeMillis() - lastFixAt <= 15_000L
            canvas.drawText("GPS " + ((if (fresh) speedKmh?.toString() else null) ?: "--") + " km/h",
                x + 9f, y + 28f * scale, label)
            val textLimit = if (fresh && roadMatch != null) "OSM: " + roadMatch!!.kmh + " km/h (tham khảo)"
                else "Giới hạn: -- (chưa rõ)"
            canvas.drawText(textLimit,
                x + 9f, y + 55f * scale, secondary)
            val status = when {
                !locationGranted() -> "Cần cấp quyền GPS trên điện thoại"
                !hasFix -> "Đang chờ vị trí GPS"
                System.currentTimeMillis() - lastFixAt > 120_000L -> "GPS đã cũ"
                roadDataError != null -> "Dữ liệu đường bị lỗi"
                roadCount == 0 -> "Chưa nhập dữ liệu maxspeed"
                else -> "SQLite RTree: " + roadCount + " đoạn"
            }
            canvas.drawText(status, x + 9f, y + 80f * scale, secondary)
            if (lastMap == null || error != null) {
                canvas.drawText(error ?: "Đang tải bản đồ OSM...",
                    x, area.centerY().toFloat(), label)
            }
            val foot = "© OpenStreetMap contributors · OpenFreeMap"
            val footY = area.bottom - 13f
            canvas.drawRoundRect(area.left + 7f, footY - 22f,
                area.left + minOf(area.width() - 7f, 500f * scale),
                footY + 9f, 6f, 6f, bg)
            secondary.textSize = 14f * scale
            canvas.drawText(foot, area.left + 13f, footY, secondary)
        } catch (e: Exception) {
            Log.w(tag, "Unable to draw host surface", e)
        } finally {
            if (canvas != null) {
                try {
                    surface.unlockCanvasAndPost(canvas)
                    CarSurfaceTelemetry.framePosted(context)
                } catch (e: Exception) {
                    Log.w(tag, "Unable to post host surface", e)
                }
            }
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        visible = Rect(visibleArea)
        CarSurfaceTelemetry.record(context, "visibleAreaChanged", width, height,
            visible, stable)
        drawFrame()
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        stable = Rect(stableArea)
        CarSurfaceTelemetry.record(context, "stableAreaChanged", width, height,
            visible, stable)
        drawFrame()
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        // A host may not forward gestures (e.g. when pan mode is unavailable).
        // Report observed events; map panning remains disabled until host test.
        CarSurfaceTelemetry.record(context, "scroll", width, height,
            visible, stable)
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        CarSurfaceTelemetry.record(context, "scale", width, height,
            visible, stable)
        if (scaleFactor.isFinite() && scaleFactor in 0.5f..2.0f) {
            zoomBy(ln(scaleFactor.toDouble()) / ln(2.0))
        }
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        CarSurfaceTelemetry.record(context, "fling", width, height,
            visible, stable)
    }

    override fun onClick(x: Float, y: Float) {
        CarSurfaceTelemetry.record(context, "click", width, height,
            visible, stable)
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        // Do not stop a newer Surface if host destroys a previous instance.
        if (targetSurface !== container.surface) {
            container.surface?.release()
            return
        }
        CarSurfaceTelemetry.record(context, "surfaceDestroyed", width, height,
            visible, stable)
        stop()
    }

    fun stop() {
        generation++
        matchSequence++
        worker?.shutdownNow()
        worker = null
        lastLocation = null
        try { locationManager.removeUpdates(locationListener) }
        catch (_: Exception) {}
        snapshotter?.cancel()
        snapshotter = null
        targetSurface?.release()
        targetSurface = null
        lastMap = null
        rendering = false
        dirty = false
    }
}
