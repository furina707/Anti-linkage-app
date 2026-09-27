package com.antilinkage.sandbox.loader

import android.content.Context
import android.util.Log
import dalvik.system.BaseDexClassLoader
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Array

/**
 * 动态注入目标 APK 的 Dex 字节码与 Native 动态库到宿主类加载器中
 * 解决 ActivityThread 反射还原 TargetActivity 时抛出 ClassNotFoundException 的问题
 */
object DexInjector {
    private const val TAG = "DexInjector"

    fun injectApk(context: Context, apkFile: File, nativeDir: File) {
        if (!apkFile.exists()) return

        try {
            val hostClassLoader = context.classLoader as? BaseDexClassLoader ?: return
            val targetClassLoader = PathClassLoader(
                apkFile.absolutePath,
                nativeDir.absolutePath,
                hostClassLoader
            )

            val pathListField = BaseDexClassLoader::class.java.getDeclaredField("pathList")
            pathListField.isAccessible = true

            val hostPathList = pathListField.get(hostClassLoader) ?: return
            val targetPathList = pathListField.get(targetClassLoader) ?: return

            val dexElementsField = hostPathList.javaClass.getDeclaredField("dexElements")
            dexElementsField.isAccessible = true

            val hostElements = dexElementsField.get(hostPathList) as kotlin.Array<*>
            val targetElements = dexElementsField.get(targetPathList) as kotlin.Array<*>

            val combined = Array.newInstance(
                hostElements.javaClass.componentType!!,
                hostElements.size + targetElements.size
            )
            // 将目标 APK dex 放在前面优先匹配
            System.arraycopy(targetElements, 0, combined, 0, targetElements.size)
            System.arraycopy(hostElements, 0, combined, targetElements.size, hostElements.size)

            dexElementsField.set(hostPathList, combined)
            Log.i(TAG, "Successfully injected dex elements for: ${apkFile.name}")

            // 尝试挂载目标 APK 资源到宿主 AssetManager
            try {
                val addAssetPathMethod = context.assets.javaClass.getDeclaredMethod("addAssetPath", String::class.java)
                addAssetPathMethod.isAccessible = true
                addAssetPathMethod.invoke(context.assets, apkFile.absolutePath)
                Log.i(TAG, "Successfully mounted assets for: ${apkFile.name}")
            } catch (t: Throwable) {
                Log.w(TAG, "addAssetPath skipped or failed: ${t.message}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to inject APK dex", t)
        }
    }
}
