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

    private fun locationGranted(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "vn.g98g00107.thanhtaxixanh.driver/config"
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "configChanged" -> {
                    sendBroadcast(Intent(ACTION_CONFIG_CHANGED).setPackage(packageName))
                    result.success(null)
                }
                "getLocationPermission" -> result.success(locationGranted())
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
                "importRoadGeoJson" -> {
                    if (pendingRoadImport != null) {
                        result.error("ALREADY_IMPORTING", "Đã có tệp đang được chọn", null)
                    } else {
                        pendingRoadImport = result
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
        if (requestCode != ROAD_IMPORT_REQUEST) return
        val pending = pendingRoadImport ?: return
        pendingRoadImport = null
        val uri: Uri? = if (resultCode == Activity.RESULT_OK) data?.data else null
        if (uri == null) {
            pending.success(null) // User canceled. Keep existing imported data.
            return
        }
        thread(name = "OsmRoadGeoJsonImport") {
            try {
                vn.g98g00107.thanhtaxixanh.driver.car.OsmRoadSqliteStore
                    .importGeoJson(applicationContext, uri)
                runOnUiThread {
                    pending.success(OsmRoadStatus())
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

    override fun onDestroy() {
        pendingLocationPermission?.error(
            "ACTIVITY_CLOSED", "Phone activity was closed during permission request", null)
        pendingLocationPermission = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONFIG_CHANGED =
            "vn.g98g00107.thanhtaxixanh.driver.CONFIG_CHANGED"
        private const val LOCATION_PERMISSION_CODE = 4281
        private const val ROAD_IMPORT_REQUEST = 4282
    }
}
