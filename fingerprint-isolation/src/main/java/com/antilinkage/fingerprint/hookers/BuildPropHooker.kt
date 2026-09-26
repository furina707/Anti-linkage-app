package com.antilinkage.fingerprint.hookers

import android.os.Build
import com.antilinkage.fingerprint.config.FingerprintManager
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 拦截并篡改 Build 硬件参数与 SystemProperties
 */
object BuildPropHooker {

    /**
     * 直接在当前进程内存中覆盖 Build 静态字段
     */
    fun applyInMemoryBuildProperties() {
        val fp = FingerprintManager.getCurrentFingerprint()
        if (!fp.isEnabled) return

        setStaticField(Build::class.java, "BRAND", fp.brand)
        setStaticField(Build::class.java, "MODEL", fp.model)
        setStaticField(Build::class.java, "MANUFACTURER", fp.manufacturer)
        setStaticField(Build::class.java, "PRODUCT", fp.product)
        setStaticField(Build::class.java, "DEVICE", fp.device)
        setStaticField(Build::class.java, "FINGERPRINT", fp.fingerprint)
        setStaticField(Build::class.java, "SERIAL", fp.serial)
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any) {
        try {
            val field: Field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true

            // 去除 final 修饰符（适配兼容老版本运行时）
            try {
                val modifiersField = Field::class.java.getDeclaredField("modifiers")
                modifiersField.isAccessible = true
                modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
            } catch (_: Throwable) {
                // 部分高版本 Android 限制 modifiers 字段反射，忽略
            }

            field.set(null, value)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取 SystemProperties.get(String, String) 目标方法用于挂钩
     */
    fun getSystemPropertiesMethod(): Method? {
        return try {
            val spClass = Class.forName("android.os.SystemProperties")
            spClass.getDeclaredMethod("get", String::class.java, String::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun handleSystemPropertiesIntercept(key: String, def: String?, originalInvoker: () -> Any?): Any? {
        val fp = FingerprintManager.getCurrentFingerprint()
        if (!fp.isEnabled) return originalInvoker()

        return when (key) {
            "ro.product.brand" -> fp.brand
            "ro.product.model" -> fp.model
            "ro.product.manufacturer" -> fp.manufacturer
            "ro.product.name" -> fp.product
            "ro.product.device" -> fp.device
            "ro.build.fingerprint" -> fp.fingerprint
            "ro.serialno" -> fp.serial
            else -> originalInvoker()
        }
    }
}
