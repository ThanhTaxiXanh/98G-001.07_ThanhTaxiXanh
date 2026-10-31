package vn.g98g00107.thanhtaxixanh.driver

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.view.View
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMapOptions

/**
 * Android phone ONLY. Does not modify the host-provided Android Auto Surface.
 * At most one MapLibre MapView is active; Flutter disposes it when hidden.
 */
class PhoneMapPreviewFactory(private val activity: Activity) :
    PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    private var view: PhoneMapPreview? = null
    private var hostStarted = false
    private var hostResumed = false

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        view?.dispose()
        val created = PhoneMapPreview(context, viewId) {
            if (view?.id == viewId) view = null
        }
        view = created
        if (hostStarted) created.start()
        if (hostResumed) created.resume()
        return created
    }

    fun command(action: String, enabled: Boolean?): Map<String, Any> =
        view?.command(action, enabled) ?: mapOf(
            "ready" to false, "status" to "Đang chờ bật bản đồ động",
            "source" to "MapLibre phone only", "fps" to 0.0,
            "javaHeapMb" to 0.0, "nativeHeapMb" to 0.0
        )

    fun onStart() {
        hostStarted = true
        view?.start()
    }

    fun onResume() {
        hostResumed = true
        view?.resume()
    }

    fun onPause() {
        hostResumed = false
        view?.pause()
    }

    fun onStop() {
        hostStarted = false
        view?.stop()
    }

    fun onLowMemory() { view?.lowMemory() }
    fun dispose() {
        view?.dispose()
        view = null
    }
}

private class PhoneMapPreview(
    private val ctx: Context,
    val id: Int,
    private val onDisposed: () -> Unit
) : PlatformView {
    private val locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val mapView: MapView
    private var map: MapLibreMap? = null
    private var destroyed = false
    private var started = false
    private var resumed = false
    private var listening = false
    private var ready = false
    private var tilt3d = false
    private var follow = true
    private var fix: Location? = null
    private var status = "Đang nạp MapLibre/tiles..."
    private var frameStartMs = SystemClock.elapsedRealtime()
    private var frames = 0
    private var sampledFps = 0.0
    private var frameLastMs = 0L
    private var lastUpdatedMs = 0L

    private val frameListener = MapView.OnDidFinishRenderingFrameListener { _, _, _ ->
        if (destroyed) return@OnDidFinishRenderingFrameListener
        frames++
        val now = SystemClock.elapsedRealtime()
        frameLastMs = now
        val duration = now - frameStartMs
        if (duration >= 1500L) {
            sampledFps = frames * 1000.0 / duration
            frameStartMs = now
            frames = 0
        }
    }

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!isUsable(location)) return
            fix = Location(location)
            lastUpdatedMs = SystemClock.elapsedRealtime()
            status = "GPS đã nhận; bản đồ trực tuyến"
            if (follow) moveToFix()
        }
    }

    init {
        MapLibre.getInstance(ctx.applicationContext)
        val options = MapLibreMapOptions.createFromAttributes(ctx, null).textureMode(true)
        mapView = MapView(ctx, options)
        mapView.onCreate(null)
        mapView.setMaximumFps(30)
        mapView.addOnDidFinishRenderingFrameListener(frameListener)
        mapView.getMapAsync { loaded ->
            if (destroyed) return@getMapAsync
            map = loaded
            loaded.uiSettings.isAttributionEnabled = true
            loaded.uiSettings.isCompassEnabled = true
            loaded.uiSettings.isZoomGesturesEnabled = true
            loaded.uiSettings.isScrollGesturesEnabled = true
            loaded.uiSettings.isRotateGesturesEnabled = true
            loaded.uiSettings.isTiltGesturesEnabled = true
            // Neutral overview is NOT the user's GPS position.
            loaded.cameraPosition = CameraPosition.Builder()
                .target(LatLng(16.0, 107.5)).zoom(5.4).build()
            loaded.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                if (!destroyed) {
                    ready = true
                    status = if (fix == null) "Bản đồ sẵn sàng · chờ GPS"
                        else "Bản đồ sẵn sàng · GPS"
                    if (fix != null && follow) moveToFix()
                }
            }
        }
    }

    private fun granted() = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED || ctx.checkSelfPermission(
        Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isUsable(loc: Location): Boolean {
        val age = System.currentTimeMillis() - loc.time
        return loc.latitude in -90.0..90.0 && loc.longitude in -180.0..180.0 &&
            age in 0L..30_000L && (!loc.hasAccuracy() || loc.accuracy <= 100f)
    }

    private fun moveToFix() {
        val loc = fix ?: return
        val m = map ?: return
        val bearing = if (loc.hasBearing() && loc.hasSpeed() && loc.speed >= 1.5f &&
            (!loc.hasBearingAccuracy() || loc.bearingAccuracyDegrees <= 45f)) {
            loc.bearing.toDouble()
        } else m.cameraPosition.bearing
        val zoom = if (m.cameraPosition.zoom < 10.0) 16.5 else m.cameraPosition.zoom
        val camera = CameraPosition.Builder()
            .target(LatLng(loc.latitude, loc.longitude))
            .zoom(zoom.coerceIn(3.0, 19.0))
            .bearing(bearing)
            .tilt(if (tilt3d) 48.0 else 0.0)
            .build()
        m.animateCamera(CameraUpdateFactory.newCameraPosition(camera), 450)
    }

    @Suppress("MissingPermission")
    private fun beginLocation() {
        if (!resumed || !granted() || listening) {
            if (!granted()) status = "Cần cấp quyền vị trí trên điện thoại"
            return
        }
        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { locationManager.isProviderEnabled(it) }
            providers.mapNotNull { locationManager.getLastKnownLocation(it) }
                .filter(::isUsable).maxByOrNull { it.time }?.let {
                    gpsListener.onLocationChanged(it)
                }
            for (provider in providers) {
                locationManager.requestLocationUpdates(
                    provider, 2500L, 3f, gpsListener, ctx.mainLooper)
            }
            listening = providers.isNotEmpty()
        } catch (e: Exception) {
            status = "Không thể nhận vị trí GPS"
            Log.w("ThanhTaxiPhoneMap", "GPS phone preview unavailable", e)
        }
    }

    private fun stopLocation() {
        if (listening) {
            try { locationManager.removeUpdates(gpsListener) } catch (_: Exception) {}
            listening = false
        }
    }

    fun command(action: String, enabled: Boolean?): Map<String, Any> {
        if (destroyed) return snapshot()
        when (action) {
            "status" -> { /* no-op */ }
            "refreshGps" -> beginLocation()
            "zoomIn" -> map?.animateCamera(CameraUpdateFactory.zoomIn(), 300)
            "zoomOut" -> map?.animateCamera(CameraUpdateFactory.zoomOut(), 300)
            "recenter" -> {
                follow = true
                moveToFix()
            }
            "set3d" -> {
                tilt3d = enabled == true
                val m = map
                if (m != null) {
                    val before = m.cameraPosition
                    m.animateCamera(CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(before.target)
                            .zoom(before.zoom).bearing(before.bearing)
                            .tilt(if (tilt3d) 48.0 else 0.0).build()), 300)
                }
            }
            else -> throw IllegalArgumentException("Lệnh bản đồ không hợp lệ")
        }
        return snapshot()
    }

    private fun snapshot(): Map<String, Any> {
        val rt = Runtime.getRuntime()
        val javaMb = (rt.totalMemory() - rt.freeMemory()) / 1048576.0
        val nativeMb = Debug.getNativeHeapAllocatedSize() / 1048576.0
        val age = SystemClock.elapsedRealtime() - lastUpdatedMs
        val fresh = fix != null && age in 0L..15_000L
        val frameAge = SystemClock.elapsedRealtime() - frameLastMs
        return mapOf(
            "ready" to ready,
            "status" to status,
            "source" to "MapLibre/OSM qua OpenFreeMap (cần mạng)",
            "hasGps" to fresh,
            "gpsAccuracyM" to (if (fresh && fix!!.hasAccuracy()) fix!!.accuracy.toDouble() else -1.0),
            "speedKmh" to (if (fresh && fix!!.hasSpeed())
                (fix!!.speed * 3.6f).toInt().coerceAtLeast(0) else -1),
            "zoom" to (map?.cameraPosition?.zoom ?: 5.4),
            "tilt3d" to tilt3d,
            "fps" to (if (frameAge in 0L..3000L) sampledFps else 0.0),
            "fpsContext" to "FPS đo từ khung MapLibre gần đây, không phải FPS liên tục",
            "frameAgeMs" to (if (frameLastMs == 0L) -1L else frameAge),
            "javaHeapMb" to javaMb,
            "nativeHeapMb" to nativeMb,
            "mapAlive" to !destroyed
        )
    }

    fun start() {
        if (!destroyed && !started) {
            mapView.onStart()
            started = true
        }
    }

    fun resume() {
        if (!destroyed && !resumed) {
            if (!started) start()
            mapView.onResume()
            resumed = true
            beginLocation()
        }
    }

    fun pause() {
        stopLocation()
        if (!destroyed && resumed) {
            mapView.onPause()
            resumed = false
        }
    }

    fun stop() {
        pause()
        if (!destroyed && started) {
            mapView.onStop()
            started = false
        }
    }

    fun lowMemory() {
        if (!destroyed) mapView.onLowMemory()
    }

    override fun getView(): View = mapView

    override fun dispose() {
        if (destroyed) return
        stop()
        destroyed = true
        mapView.removeOnDidFinishRenderingFrameListener(frameListener)
        mapView.onDestroy()
        map = null
        fix = null
        onDisposed()
    }
}
