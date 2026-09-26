package com.antilinkage.fingerprint.hookers

import android.net.wifi.WifiInfo
import com.antilinkage.fingerprint.config.FingerprintManager
import java.lang.reflect.Method
import java.net.NetworkInterface

/**
 * 拦截 WiFi 与物理网卡 MAC 地址
 */
object NetworkMacHooker {

    fun getWifiInfoMacMethod(): Method? {
        return try {
            WifiInfo::class.java.getDeclaredMethod("getMacAddress")
        } catch (e: Exception) {
            null
        }
    }

    fun getNetworkInterfaceMacMethod(): Method? {
        return try {
            NetworkInterface::class.java.getDeclaredMethod("getHardwareAddress")
        } catch (e: Exception) {
            null
        }
    }

    fun parseMacToBytes(mac: String): ByteArray {
        val parts = mac.split(":")
        val bytes = ByteArray(parts.size)
        for (i in parts.indices) {
            val intVal = Integer.parseInt(parts[i], 16)
            bytes[i] = intVal.toByte()
        }
        return bytes
    }

    fun handleWifiInfoMac(originalInvoker: () -> Any?): Any? {
        val fp = FingerprintManager.getCurrentFingerprint()
        if (fp.isEnabled && fp.macAddress.isNotEmpty()) {
            return fp.macAddress
        }
        return originalInvoker()
    }

    fun handleHardwareAddress(originalInvoker: () -> Any?): Any? {
        val fp = FingerprintManager.getCurrentFingerprint()
        if (fp.isEnabled && fp.macAddress.isNotEmpty()) {
            return parseMacToBytes(fp.macAddress)
        }
        return originalInvoker()
    }
}
