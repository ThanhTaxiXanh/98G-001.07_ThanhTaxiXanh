package vn.g98g00107.thanhtaxixanh.driver

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "vn.g98g00107.thanhtaxixanh.driver/config"
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "configChanged" -> {
                    // SharedPreferences is persisted by Dart before this callback.
                    sendBroadcast(Intent(ACTION_CONFIG_CHANGED).setPackage(packageName))
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }
    companion object {
        const val ACTION_CONFIG_CHANGED =
            "vn.g98g00107.thanhtaxixanh.driver.CONFIG_CHANGED"
    }
}
