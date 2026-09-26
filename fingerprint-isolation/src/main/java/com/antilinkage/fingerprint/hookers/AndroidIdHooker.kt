package com.antilinkage.fingerprint.hookers

import android.provider.Settings
import com.antilinkage.fingerprint.config.FingerprintManager
import java.lang.reflect.Method

/**
 * 拦截 Settings.Secure.getString(ContentResolver, String)
 * 当查询目标为 Settings.Secure.ANDROID_ID 时，返回当前分身独立的虚拟 Android ID
 */
object AndroidIdHooker {

    fun getTargetMethod(): Method? {
        return try {
            Settings.Secure::class.java.getDeclaredMethod(
                "getString",
                android.content.ContentResolver::class.java,
                String::class.java
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 核心拦截处理逻辑
     */
    fun handleIntercept(args: List<Any?>, originalInvoker: () -> Any?): Any? {
        val name = args.getOrNull(1) as? String
        if (Settings.Secure.ANDROID_ID == name) {
            val fp = FingerprintManager.getCurrentFingerprint()
            if (fp.isEnabled && fp.androidId.isNotEmpty()) {
                return fp.androidId
            }
        }
        return originalInvoker()
    }
}
