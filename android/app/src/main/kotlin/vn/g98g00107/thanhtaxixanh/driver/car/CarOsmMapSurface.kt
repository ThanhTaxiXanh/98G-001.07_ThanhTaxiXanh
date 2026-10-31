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
    private var roads: List<OsmRoadSegment> = emptyList()
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
        generation++
        snapshotter?.cancel()
        snapshotter = null
        rendering = false
        dirty = false
        lastMap = null
        worker?.shutdownNow()
        worker = Executors.newSingleThreadExecutor()
        roads = emptyList()
        roadMatch = null
        roadDataError = null
        targetSurface = container.surface
        width = container.width
        height = container.height
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
            val parsed = runCatching { OsmRoadCatalog.load(context) }
            mainHandler.post {
                if (version == generation) {
                    roads = parsed.getOrNull()?.roads ?: emptyList()
                    roadDataError = parsed.exceptionOrNull()?.message
                    Log.i(tag, "Imported OSM speed segments: " + roads.size)
                    drawFrame()
                }
            }
        }
    }

    private fun matchRoadSpeed(location: Location) {
        roadMatch = null
        val dataset = roads
        val queue = worker ?: return
        if (dataset.isEmpty()) return
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
            val matched = OsmRoadMatcher.match(
                dataset, lat, lon, accuracy, bearing, speed)
            mainHandler.post {
                if (version == generation && request == matchSequence) {
                    roadMatch = matched
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

    private fun safeArea(): Rect {
        val rect = Rect(stable ?: visible ?: Rect(0, 0, width, height))
        if (!rect.intersect(0, 0, width, height) ||
            rect.width() < 130 || rect.height() < 100) {
            return Rect(0, 0, width, height)
        }
        return rect
    }

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
            val area = safeArea()
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
                roads.isEmpty() -> "Chưa nhập dữ liệu maxspeed"
                else -> "OSM maxspeed: " + roads.size + " đoạn"
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
                try { surface.unlockCanvasAndPost(canvas) }
                catch (e: Exception) { Log.w(tag, "Unable to post host surface", e) }
            }
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        visible = Rect(visibleArea)
        drawFrame()
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        stable = Rect(stableArea)
        drawFrame()
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) { stop() }

    fun stop() {
        generation++
        matchSequence++
        worker?.shutdownNow()
        worker = null
        try { locationManager.removeUpdates(locationListener) }
        catch (_: Exception) {}
        snapshotter?.cancel()
        snapshotter = null
        targetSurface = null
        lastMap = null
        rendering = false
        dirty = false
    }
}
