package com.antilinkage.fingerprint.config

import java.io.Serializable
import java.util.UUID

/**
 * 虚拟指纹配置实体类
 * 每个多开分身实例对应一份独立且持久化的指纹配置
 */
data class InstanceFingerprint(
    val userId: Int = 0,
    val packageName: String = "",
    val androidId: String = generateRandomHex(16),
    val imei: String = generateRandomNumeric(15),
    val meid: String = generateRandomNumeric(14),
    val macAddress: String = generateRandomMac(),
    val brand: String = "Google",
    val model: String = "Pixel 7 Pro",
    val manufacturer: String = "Google",
    val product: String = "cheetah",
    val device: String = "cheetah",
    val fingerprint: String = "google/cheetah/cheetah:13/TQ3A.230901.001/10750766:user/release-keys",
    val serial: String = generateRandomAlphanumeric(10),
    val oaid: String = UUID.randomUUID().toString(),
    val latitude: Double = 39.9042,
    val longitude: Double = 116.4074,
    val isEnabled: Boolean = true
) : Serializable {

    companion object {
        fun generateRandomHex(length: Int): String {
            val chars = "0123456789abcdef"
            return (1..length).map { chars.random() }.joinToString("")
        }

        fun generateRandomNumeric(length: Int): String {
            val chars = "0123456789"
            return (1..length).map { chars.random() }.joinToString("")
        }

        fun generateRandomAlphanumeric(length: Int): String {
            val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            return (1..length).map { chars.random() }.joinToString("")
        }

        fun generateRandomMac(): String {
            val chars = "0123456789ABCDEF"
            return (1..6).joinToString(":") {
                "${chars.random()}${chars.random()}"
            }
        }
    }
}
