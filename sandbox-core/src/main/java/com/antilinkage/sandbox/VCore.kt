package com.antilinkage.sandbox

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.antilinkage.fingerprint.AntiLinkageXposedModule
import com.antilinkage.fingerprint.config.FingerprintManager
import com.antilinkage.fingerprint.config.InstanceFingerprint
import com.antilinkage.sandbox.hook.ActivityThreadHook
import com.antilinkage.sandbox.hook.IActivityTaskManagerHook
import com.antilinkage.sandbox.hook.IPackageManagerHook
import com.antilinkage.sandbox.loader.VClassLoader
import com.antilinkage.sandbox.vfs.VFileSystem
import java.io.File

/**
 * 虚拟化沙箱核心控制器 (VCore)
 * 统筹 VFS 隔离、Binder 劫持、类加载及 LibXposed 指纹拦截注入
 */
object VCore {
    private const val TAG = "VCore"
    private var isInitialized = false

    /**
     * 初始化沙箱底座环境
     */
    fun init(context: Context) {
        if (isInitialized) return
        try {
            unsealHiddenApi()
            isInitialized = true
            Log.i(TAG, "VCore initialized successfully.")
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing VCore", t)
        }
    }

    /**
     * 豁免 Android 9.0+ 的 Hidden API 反射限制
     */
    private fun unsealHiddenApi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
                val getDeclaredMethod = Class::class.java.getDeclaredMethod(
                    "getDeclaredMethod",
                    String::class.java,
                    arrayOf<Class<*>>()::class.java
                )
                val vmRuntimeClass = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
                val getRuntime = getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null) as java.lang.reflect.Method
                val vmRuntime = getRuntime.invoke(null)
                val setHiddenApiExemptions = getDeclaredMethod.invoke(
                    vmRuntimeClass,
                    "setHiddenApiExemptions",
                    arrayOf(arrayOf<String>()::class.java)
                ) as java.lang.reflect.Method

                // 传入 "L" 豁免所有隐藏 API
                setHiddenApiExemptions.invoke(vmRuntime, arrayOf("L"))
                Log.i(TAG, "Hidden API restriction successfully unsealed.")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to unseal hidden api", t)
            }
        }
    }

    /**
     * 启动指定多开分身实例
     * @param context 宿主上下文
     * @param targetApk 目标 APK 文件
     * @param userId 分身用户 ID (0, 1, 2...)
     * @param customFingerprint 目标分身专属定制的指纹配置
     */
    fun launchVirtualApp(
        context: Context,
        targetApk: File,
        userId: Int,
        customFingerprint: InstanceFingerprint? = null
    ) {
        init(context)

        // 1. 解析目标 APK 信息
        val pm = context.packageManager
        val packageInfo = pm.getPackageArchiveInfo(
            targetApk.absolutePath,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
        ) ?: run {
            Log.e(TAG, "Cannot parse target APK: ${targetApk.absolutePath}")
            return
        }

        val targetPackageName = packageInfo.packageName
        packageInfo.applicationInfo.sourceDir = targetApk.absolutePath
        packageInfo.applicationInfo.publicSourceDir = targetApk.absolutePath

        // 2. 初始化分身专属文件系统
        val userRoot = VFileSystem.initUserEnvironment(context, targetPackageName, userId)
        val configFile = VFileSystem.getFingerprintConfigFile(context, targetPackageName, userId)

        // 3. 写入独立虚拟指纹配置
        val fp = customFingerprint ?: InstanceFingerprint(userId = userId, packageName = targetPackageName)
        FingerprintManager.saveToFile(configFile, fp)
        FingerprintManager.setCurrentFingerprint(fp)

        // 4. 创建隔离 ClassLoader
        val nativeDir = File(userRoot, "lib")
        val classLoader = VClassLoader.create(
            targetApk,
            nativeDir,
            context.classLoader
        )

        // 5. 挂载系统服务钩子
        IPackageManagerHook.install(context, targetPackageName, packageInfo)
        IActivityTaskManagerHook.install(context, targetPackageName)
        ActivityThreadHook.install(classLoader)

        // 6. 载入 LibXposed 规范指纹拦截逻辑
        AntiLinkageXposedModule.installDirectHooks(classLoader, configFile)

        // 7. 查找主启动入口 Activity 并拉起
        val launchActivity = packageInfo.activities?.firstOrNull()?.name
        if (launchActivity != null) {
            val intent = Intent().apply {
                setClassName(targetPackageName, launchActivity)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Log.i(TAG, "Launching virtual app [$targetPackageName] for user $userId via $launchActivity")
            context.startActivity(intent)
        } else {
            Log.e(TAG, "No activity found in target APK!")
        }
    }
}
