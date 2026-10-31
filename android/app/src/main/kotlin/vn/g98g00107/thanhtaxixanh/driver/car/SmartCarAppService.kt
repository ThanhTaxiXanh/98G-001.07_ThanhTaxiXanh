package vn.g98g00107.thanhtaxixanh.driver.car

import android.content.Intent
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import android.content.pm.ApplicationInfo

/**
 * Android Auto projection entry point. No arbitrary Flutter widgets on AA.
 * Host chrome and visible map viewport are controlled by Android Auto.
 */
class SmartCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        else HostValidator.Builder(this)
            .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
            .build()

    override fun onCreateSession(): Session = SmartSession()
}

private class SmartSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        Log.i("ThanhTaxiCar", "AA session created; actual dashboard still unverified")
        carContext.getCarService(AppManager::class.java)
            .setSurfaceCallback(CarDiagnosticSurface())
        return object : Screen(carContext) {
            override fun onGetTemplate(): Template = NavigationTemplate.Builder().build()
        }
    }
}
