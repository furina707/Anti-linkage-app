package com.antilinkage.fingerprint.hookers

import android.location.Location
import android.location.LocationManager
import com.antilinkage.fingerprint.config.FingerprintManager
import java.lang.reflect.Method

/**
 * 拦截地理位置与经纬度
 */
object LocationHooker {

    fun getTargetMethod(): Method? {
        return try {
            LocationManager::class.java.getDeclaredMethod("getLastKnownLocation", String::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun handleIntercept(provider: String?, originalInvoker: () -> Any?): Any? {
        val fp = FingerprintManager.getCurrentFingerprint()
        if (!fp.isEnabled) return originalInvoker()

        val location = Location(provider ?: LocationManager.GPS_PROVIDER).apply {
            latitude = fp.latitude
            longitude = fp.longitude
            accuracy = 5.0f
            time = System.currentTimeMillis()
        }
        return location
    }
}
