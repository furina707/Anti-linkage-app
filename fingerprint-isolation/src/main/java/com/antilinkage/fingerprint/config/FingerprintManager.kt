package com.antilinkage.fingerprint.config

import com.google.gson.Gson
import java.io.File

/**
 * 指纹配置存取管理器
 * 支持宿主应用写入，并由被 Hook 的分身进程读取
 */
object FingerprintManager {
    private val gson = Gson()
    private var cachedFingerprint: InstanceFingerprint? = null

    /**
     * 设置并缓存当前进程指纹
     */
    fun setCurrentFingerprint(fingerprint: InstanceFingerprint) {
        cachedFingerprint = fingerprint
    }

    /**
     * 获取当前进程生效的指纹，若未初始化则返回默认随机指纹
     */
    fun getCurrentFingerprint(): InstanceFingerprint {
        return cachedFingerprint ?: InstanceFingerprint().also { cachedFingerprint = it }
    }

    /**
     * 从持久化文件加载指定分身用户的指纹
     */
    fun loadFromFile(configFile: File): InstanceFingerprint? {
        return try {
            if (configFile.exists() && configFile.canRead()) {
                val json = configFile.readText()
                val fp = gson.fromJson(json, InstanceFingerprint::class.java)
                cachedFingerprint = fp
                fp
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 保存指纹配置到磁盘
     */
    fun saveToFile(configFile: File, fingerprint: InstanceFingerprint): Boolean {
        return try {
            configFile.parentFile?.mkdirs()
            configFile.writeText(gson.toJson(fingerprint))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
