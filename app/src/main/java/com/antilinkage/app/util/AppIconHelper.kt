package com.antilinkage.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.content.ContextCompat
import com.antilinkage.app.model.ClonedAppInfo
import java.io.File

object AppIconHelper {

    private val iconCache = LruCache<String, Drawable>(64)

    /**
     * 提取多开分身 APK 或已安装宿主包的真实图标
     */
    fun getAppIcon(context: Context, info: ClonedAppInfo): Drawable {
        val cacheKey = "${info.packageName}_${info.apkFile.absolutePath}_${info.apkFile.lastModified()}"
        iconCache.get(cacheKey)?.let { return it }

        val pm = context.packageManager

        // 1. 如果 APK 文件存在，尝试从 APK 离线包静态提取高清图标
        if (info.apkFile.exists()) {
            try {
                val pkgInfo = pm.getPackageArchiveInfo(info.apkFile.absolutePath, 0)
                if (pkgInfo != null) {
                    val appInfo = pkgInfo.applicationInfo
                    appInfo.sourceDir = info.apkFile.absolutePath
                    appInfo.publicSourceDir = info.apkFile.absolutePath
                    val icon = appInfo.loadIcon(pm)
                    if (icon != null) {
                        iconCache.put(cacheKey, icon)
                        return icon
                    }
                }
            } catch (_: Throwable) {}
        }

        // 2. 如果手机系统内安装了相同包名，从系统直接获取图标
        try {
            val appInfo = pm.getApplicationInfo(info.packageName, 0)
            val icon = pm.getApplicationIcon(appInfo)
            iconCache.put(cacheKey, icon)
            return icon
        } catch (_: Throwable) {}

        // 3. 提取宿主应用图标或系统默认图标作为兜底
        val fallbackIcon = try {
            pm.getApplicationIcon(context.packageName)
        } catch (_: Throwable) {
            ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)!!
        }
        iconCache.put(cacheKey, fallbackIcon)
        return fallbackIcon
    }

    /**
     * 提取 APK 的真实应用名称
     */
    fun getAppLabel(context: Context, apkFile: File, fallbackName: String): String {
        if (!apkFile.exists()) return fallbackName
        val pm = context.packageManager
        try {
            val pkgInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            if (pkgInfo != null) {
                val appInfo = pkgInfo.applicationInfo
                appInfo.sourceDir = apkFile.absolutePath
                appInfo.publicSourceDir = apkFile.absolutePath
                val label = appInfo.loadLabel(pm).toString()
                if (label.isNotBlank()) return label
            }
        } catch (_: Throwable) {}
        return fallbackName
    }
}
