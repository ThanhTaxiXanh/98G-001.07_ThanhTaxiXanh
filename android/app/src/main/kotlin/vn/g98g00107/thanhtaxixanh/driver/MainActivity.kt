package vn.g98g00107.thanhtaxixanh.driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var pendingLocationPermission: MethodChannel.Result? = null

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
    }
}
