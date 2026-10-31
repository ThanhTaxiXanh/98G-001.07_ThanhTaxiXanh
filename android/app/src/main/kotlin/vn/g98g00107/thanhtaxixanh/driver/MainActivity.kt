package vn.g98g00107.thanhtaxixanh.driver

import android.Manifest
import android.app.Activity
import android.net.Uri
import kotlin.concurrent.thread
import android.content.Intent
import android.content.pm.PackageManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var pendingLocationPermission: MethodChannel.Result? = null
    private var pendingRoadImport: MethodChannel.Result? = null
    private var pendingRoadAppend = false
    private var pendingTopologyImport = false
    private var pendingGpxImport: MethodChannel.Result? = null
    private var pendingTrafficImport: MethodChannel.Result? = null
    private var pendingTrafficAppend = true
    private var phoneMapPreviewFactory: PhoneMapPreviewFactory? = null

    private fun locationGranted(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        phoneMapPreviewFactory = PhoneMapPreviewFactory(this)
        flutterEngine.platformViewsController.registry.registerViewFactory(
            "vn.g98g00107.thanhtaxixanh.driver/phone-map-view",
            phoneMapPreviewFactory!!
        )
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "vn.g98g00107.thanhtaxixanh.driver/config"
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "configChanged" -> {
                    sendBroadcast(Intent(ACTION_CONFIG_CHANGED).setPackage(packageName))
                    result.success(null)
                }
                "getCarSurfaceDiagnostics" -> {
                    result.success(
                        vn.g98g00107.thanhtaxixanh.driver.car.CarSurfaceTelemetry
                            .snapshot(applicationContext)
                    )
                }
                "clearCarSurfaceDiagnostics" -> {
                    vn.g98g00107.thanhtaxixanh.driver.car.CarSurfaceTelemetry
                        .clear(applicationContext)
                    result.success(
                        vn.g98g00107.thanhtaxixanh.driver.car.CarSurfaceTelemetry
                            .snapshot(applicationContext)
                    )
                }
                "getLocationPermission" -> result.success(locationGranted())
                "phoneMapPreviewCommand" -> {
                    try {
                        val action = call.argument<String>("action") ?: "status"
                        val enabled = call.argument<Boolean>("enabled")
                        result.success(phoneMapPreviewFactory?.command(action, enabled)
                            ?: mapOf("ready" to false, "status" to "Bản đồ chưa khởi tạo"))
                    } catch (e: Exception) {
                        result.error("PHONE_MAP_COMMAND", e.message, null)
                    }
                }
                "reviewTrafficPointsNear" -> {
                    val lat = call.argument<Double>("lat")
                    val lon = call.argument<Double>("lon")
                    if (lat == null || lon == null) {
                        result.error("INVALID_LOCATION", "Cần lat và lon", null)
                    } else {
                        thread(name = "TrafficPointPhoneReview") {
                            try {
                                val report = vn.g98g00107.thanhtaxixanh.driver.car
                                    .TrafficPointReviewService.inspect(applicationContext, lat, lon)
                                runOnUiThread { result.success(report) }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    result.error("TRAFFIC_REVIEW_FAILED", e.message, null)
                                }
                            }
                        }
                    }
                }
                "getTrafficPointStatus" -> {
                    thread(name = "TrafficPointStatus") {
                        try {
                            val status = vn.g98g00107.thanhtaxixanh.driver.car.TrafficPointStore
                                .status(applicationContext)
                            runOnUiThread { result.success(status) }
                        } catch (e: Exception) {
                            runOnUiThread { result.error("TRAFFIC_STATUS", e.message, null) }
                        }
                    }
                }
                "clearTrafficPoints" -> {
                    thread(name = "TrafficPointClear") {
                        try {
                            val status = vn.g98g00107.thanhtaxixanh.driver.car.TrafficPointStore
                                .clear(applicationContext)
                            runOnUiThread { result.success(status) }
                        } catch (e: Exception) {
                            runOnUiThread { result.error("TRAFFIC_CLEAR", e.message, null) }
                        }
                    }
                }
                "importTrafficPointGeoJson" -> {
                    if (pendingTrafficImport != null) {
                        result.error("ALREADY_IMPORTING", "Đang chọn một tệp biển báo", null)
                    } else {
                        pendingTrafficImport = result
                        pendingTrafficAppend = call.argument<Boolean>("append") != false
                        try {
                            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("*/*")
                                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                                    "application/geo+json", "application/json",
                                    "text/plain", "application/octet-stream"
                                ))
                            startActivityForResult(picker, TRAFFIC_POINT_IMPORT_REQUEST)
                        } catch (e: Exception) {
                            pendingTrafficImport = null
                            result.error("PICKER_UNAVAILABLE", e.message, null)
                        }
                    }
                }
                "getRoadDataStatus" -> {
                    thread(name = "RoadDbStatus") {
                        try {
                            vn.g98g00107.thanhtaxixanh.driver.car.OsmRoadSqliteStore
                                .migrateLegacy(applicationContext)
                            val status = OsmRoadStatus()
                            runOnUiThread { result.success(status) }
                        } catch (e: Exception) {
                            runOnUiThread { result.error("DB_STATUS", e.message, null) }
                        }
                    }
                }
                "getRoadQuality" -> {
                    thread(name = "RoadQualityAudit") {
                        try {
                            vn.g98g00107.thanhtaxixanh.driver.car.OsmRoadSqliteStore
                                .migrateLegacy(applicationContext)
                            val quality = vn.g98g00107.thanhtaxixanh.driver.car.OsmRoadSqliteStore
                                .audit(applicationContext).asMap()
                            runOnUiThread { result.success(quality) }
                        } catch (e: Exception) {
                            runOnUiThread {
                                result.error("ROAD_AUDIT_FAILED", e.message, null)
                            }
                        }
                    }
                }
                "cleanLegacyRoadDuplicates" -> {
                    thread(name = "RoadLegacyCleanup") {
                        try {
                            val quality = vn.g98g00107.thanhtaxixanh.driver.car.OsmRoadSqliteStore
                                .cleanLegacyDuplicates(applicationContext).asMap()
                            val status = OsmRoadStatus()
                            runOnUiThread {
                                result.success(mapOf("quality" to quality, "status" to status))
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                result.error("ROAD_CLEANUP_FAILED", e.message, null)
                            }
                        }
                    }
                }
                "reviewGpxTrack" -> {
                    if (pendingGpxImport != null) {
                        result.error("ALREADY_IMPORTING", "Đang chọn tệp GPX", null)
                    } else {
                        pendingGpxImport = result
                        try {
                            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("*/*")
                                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                                    "application/gpx+xml", "application/xml", "text/xml",
                                    "application/octet-stream"
                                ))
                            startActivityForResult(picker, GPX_REVIEW_REQUEST)
                        } catch (e: Exception) {
                            pendingGpxImport = null
                            result.error("PICKER_UNAVAILABLE", e.message, null)
                        }
                    }
                }
                "importRoadTopologyGeoJson" -> {
                    if (pendingRoadImport != null) {
                        result.error("ALREADY_IMPORTING", "Đang chọn tệp đường", null)
                    } else {
                        pendingRoadImport = result
                        pendingRoadAppend = false
                        pendingTopologyImport = true
                        try {
                            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("*/*")
                            startActivityForResult(picker, ROAD_IMPORT_REQUEST)
                        } catch (e: Exception) {
                            pendingRoadImport = null
                            pendingTopologyImport = false
                            result.error("PICKER_UNAVAILABLE", e.message, null)
                        }
                    }
                }
                "importRoadGeoJson" -> {
                    if (pendingRoadImport != null) {
                        result.error("ALREADY_IMPORTING", "Đã có tệp đang được chọn", null)
                    } else {
                        pendingRoadImport = result
                        pendingRoadAppend = call.argument<Boolean>("append") == true
                        try {
                            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("*/*")
                                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                                    "application/geo+json",
                                    "application/json",
                                    "text/plain",
                                    "application/octet-stream"
                                ))
                            startActivityForResult(picker, ROAD_IMPORT_REQUEST)
                        } catch (e: Exception) {
                            pendingRoadImport = null
                            result.error("PICKER_UNAVAILABLE", e.message, null)
                        }
                    }
                }
                "clearRoadGeoJson" -> {
                    thread(name = "RoadDbClear") {
                        try {
                            vn.g98g00107.thanhtaxixanh.driver.car.OsmRoadSqliteStore
                                .clear(applicationContext)
                            val status = OsmRoadStatus()
                            runOnUiThread { result.success(status) }
                        } catch (e: Exception) {
                            runOnUiThread { result.error("DELETE_FAILED", e.message, null) }
                        }
                    }
                }
                "requestLocationPermission" -> {
                    when {
                        locationGranted() -> result.success(true)
                        pendingLocationPermission != null ->
                            result.error("ALREADY_REQUESTING", "Location prompt already shown", null)
                        else -> {
                            pendingLocationPermission = result
                            requestPermissions(arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ), LOCATION_PERMISSION_CODE)
                        }
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun OsmRoadStatus(): Map<String, Any> =
        vn.g98g00107.thanhtaxixanh.driver.car.OsmRoadSqliteStore.status(applicationContext)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GPX_REVIEW_REQUEST) {
            val pending = pendingGpxImport ?: return
            pendingGpxImport = null
            val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
            if (uri == null) {
                pending.success(null)
                return
            }
            thread(name = "OfflineGpxReview") {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(16_384)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(out.size() + count <= 8_000_000) { "GPX vượt 8 MB" }
                            out.write(buffer, 0, count)
                        }
                        out.toByteArray()
                    } ?: throw IllegalArgumentException("Không đọc được GPX")
                    val parsed = vn.g98g00107.thanhtaxixanh.driver.car.GpxTraceParser
                        .parse(bytes.toString(Charsets.UTF_8))
                    val motion = vn.g98g00107.thanhtaxixanh.driver.car.GpxMotionEstimator
                        .enrich(parsed)
                    val coverage = vn.g98g00107.thanhtaxixanh.driver.car
                        .GpxOsmCoverageReview.inspectOffline(applicationContext, motion.fixes)
                    val summary = mapOf(
                        "coverageCandidate" to coverage.candidatePoints,
                        "coverageMissing" to coverage.noRoadPoints,
                        "coverageOverloaded" to coverage.overloadedPoints,
                        "coverageAmbiguous" to coverage.ambiguousPoints,
                        "status" to "UNVERIFIED_REVIEW_ONLY",
                        "points" to parsed.fixes.size,
                        "rejected" to parsed.rejectedPoints,
                        "headings" to motion.inferredHeadings,
                        "uncertain" to motion.uncertainPoints
                    )
                    runOnUiThread { pending.success(summary) }
                } catch (e: Exception) {
                    runOnUiThread {
                        pending.error("INVALID_GPX", e.message ?: "GPX không hợp lệ", null)
                    }
                }
            }
            return
        }
        if (requestCode == TRAFFIC_POINT_IMPORT_REQUEST) {
            val pending = pendingTrafficImport ?: return
            pendingTrafficImport = null
            val append = pendingTrafficAppend
            pendingTrafficAppend = true
            val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
            if (uri == null) {
                pending.success(null)
                return
            }
            thread(name = "TrafficPointGeoJsonImport") {
                try {
                    val status = vn.g98g00107.thanhtaxixanh.driver.car.TrafficPointStore
                        .importGeoJson(applicationContext, uri, append)
                    runOnUiThread { pending.success(status) }
                } catch (e: Exception) {
                    runOnUiThread {
                        pending.error("INVALID_TRAFFIC_GEOJSON",
                            e.message ?: "Dữ liệu biển báo không hợp lệ", null)
                    }
                }
            }
            return
        }
        if (requestCode != ROAD_IMPORT_REQUEST) return
        val pending = pendingRoadImport ?: return
        pendingRoadImport = null
        val append = pendingRoadAppend
        val strictTopology = pendingTopologyImport
        pendingRoadAppend = false
        pendingTopologyImport = false
        val uri: Uri? = if (resultCode == Activity.RESULT_OK) data?.data else null
        if (uri == null) {
            pending.success(null) // User canceled. Keep existing imported data.
            return
        }
        thread(name = "OsmRoadGeoJsonImport") {
            try {
                if (strictTopology) {
                    val bytes = contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(16_384)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(out.size() + count <= 8_000_000) { "GeoJSON vượt 8 MB" }
                            out.write(buffer, 0, count)
                        }
                        out.toByteArray()
                    } ?: throw IllegalArgumentException("Không đọc được tệp")
                    vn.g98g00107.thanhtaxixanh.driver.car.OsmRoadSqliteStore
                        .importTopologyGeoJson(applicationContext,
                            bytes.toString(Charsets.UTF_8), "User selected GeoJSON")
                } else {
                    vn.g98g00107.thanhtaxixanh.driver.car.OsmRoadSqliteStore
                        .importGeoJson(applicationContext, uri, append)
                }
                // SQLite must finish on the worker, not inside the UI callback.
                val status = OsmRoadStatus()
                runOnUiThread {
                    pending.success(status)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    pending.error("INVALID_GEOJSON", e.message ?: "Dữ liệu không hợp lệ", null)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE) {
            pendingLocationPermission?.success(locationGranted())
            pendingLocationPermission = null
        }
    }

    override fun onStart() {
        super.onStart()
        phoneMapPreviewFactory?.onStart()
    }

    override fun onResume() {
        super.onResume()
        phoneMapPreviewFactory?.onResume()
    }

    override fun onPause() {
        phoneMapPreviewFactory?.onPause()
        super.onPause()
    }

    override fun onStop() {
        phoneMapPreviewFactory?.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        phoneMapPreviewFactory?.onLowMemory()
    }

    override fun onDestroy() {
        phoneMapPreviewFactory?.dispose()
        phoneMapPreviewFactory = null
        pendingLocationPermission?.error(
            "ACTIVITY_CLOSED", "Phone activity was closed during permission request", null)
        pendingLocationPermission = null
        pendingTrafficImport?.error(
            "ACTIVITY_CLOSED", "Đã đóng màn hình khi chọn tệp biển báo", null)
        pendingTrafficImport = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONFIG_CHANGED =
            "vn.g98g00107.thanhtaxixanh.driver.CONFIG_CHANGED"
        private const val LOCATION_PERMISSION_CODE = 4281
        private const val ROAD_IMPORT_REQUEST = 4282
        private const val TRAFFIC_POINT_IMPORT_REQUEST = 4283
        private const val GPX_REVIEW_REQUEST = 4284
    }
}
