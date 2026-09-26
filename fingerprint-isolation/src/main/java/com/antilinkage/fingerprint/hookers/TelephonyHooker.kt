package com.antilinkage.fingerprint.hookers

import android.telephony.TelephonyManager
import com.antilinkage.fingerprint.config.FingerprintManager
import java.lang.reflect.Method

/**
 * 拦截基带与通信相关硬件标识 (IMEI, MEID, IMSI)
 */
object TelephonyHooker {

    fun getTargetMethods(): List<Method> {
        val list = mutableListOf<Method>()
        val clazz = TelephonyManager::class.java

        val methodNames = listOf(
            "getDeviceId",
            "getImei",
            "getMeid",
            "getSubscriberId",
            "getSimSerialNumber"
        )

        for (name in methodNames) {
            try {
                // 无参版本
                list.add(clazz.getDeclaredMethod(name))
            } catch (_: Throwable) {}

            try {
                // 带有 slotId 的重载版本 (int)
                list.add(clazz.getDeclaredMethod(name, Int::class.javaPrimitiveType))
            } catch (_: Throwable) {}
        }
        return list
    }

    fun handleIntercept(methodName: String, originalInvoker: () -> Any?): Any? {
        val fp = FingerprintManager.getCurrentFingerprint()
        if (!fp.isEnabled) return originalInvoker()

        return when {
            methodName.contains("Imei", ignoreCase = true) || methodName == "getDeviceId" -> fp.imei
            methodName.contains("Meid", ignoreCase = true) -> fp.meid
            methodName.contains("SimSerialNumber", ignoreCase = true) -> "898600" + fp.imei.takeLast(14)
            methodName.contains("SubscriberId", ignoreCase = true) -> "46000" + fp.imei.takeLast(10)
            else -> originalInvoker()
        }
    }
}
