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
import com.antilinkage.sandbox.loader.DexInjector
import com.antilinkage.sandbox.loader.VClassLoader
import com.antilinkage.sandbox.vfs.VFileSystem
import java.io.File
import java.io.FileNotFoundException

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

        Log.i(TAG, "Starting launchVirtualApp for user $userId, target: ${targetApk.absolutePath}")

        // 1. 校验目标 APK 是否存在
        if (!targetApk.exists()) {
            throw FileNotFoundException("目标 APK 文件不存在: ${targetApk.absolutePath}")
        }

        val pm = context.packageManager
        var packageInfo: PackageInfo? = null

        // 尝试从 APK 归档静态解析 PackageInfo
        try {
            packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    targetApk.absolutePath,
                    PackageManager.PackageInfoFlags.of((PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES).toLong())
                )
            } else {
                pm.getPackageArchiveInfo(
                    targetApk.absolutePath,
                    PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to getPackageArchiveInfo: ${t.message}")
        }

        // 如果读取 APK 归档返回 null，检查 customFingerprint 中指定的包名是否安装在系统
        val targetPackageName = packageInfo?.packageName
            ?: customFingerprint?.packageName
            ?: run {
                throw IllegalStateException("无法解析目标 APK 包信息: ${targetApk.absolutePath}")
            }

        // 从系统加载已安装包作为补充（支持已安装系统应用多开）
        if (packageInfo == null) {
            packageInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(
                        targetPackageName,
                        PackageManager.PackageInfoFlags.of((PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES).toLong())
                    )
                } else {
                    pm.getPackageInfo(targetPackageName, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES)
                }
            } catch (t: Throwable) {
                null
            }
        }

        if (packageInfo != null) {
            packageInfo.applicationInfo.sourceDir = targetApk.absolutePath
            packageInfo.applicationInfo.publicSourceDir = targetApk.absolutePath
        }

        // 2. 初始化分身专属文件系统
        val userRoot = VFileSystem.initUserEnvironment(context, targetPackageName, userId)
        val configFile = VFileSystem.getFingerprintConfigFile(context, targetPackageName, userId)

        // 3. 写入独立虚拟指纹配置
        val fp = customFingerprint ?: InstanceFingerprint(userId = userId, packageName = targetPackageName)
        FingerprintManager.saveToFile(configFile, fp)
        FingerprintManager.setCurrentFingerprint(fp)

        // 4. 创建隔离 ClassLoader 与 Dex 注入
        val nativeDir = File(userRoot, "lib").apply { if (!exists()) mkdirs() }
        val classLoader = VClassLoader.create(
            targetApk,
            nativeDir,
            context.classLoader
        )

        // 动态将目标 APK 的 Dex 注入到宿主类加载器中，防止 ActivityThread 实例化 TargetActivity 时 ClassNotFound
        DexInjector.injectApk(context, targetApk, nativeDir)

        // 5. 挂载系统服务钩子
        IPackageManagerHook.install(context, targetPackageName, packageInfo)
        IActivityTaskManagerHook.install(context, targetPackageName)
        ActivityThreadHook.install(classLoader)

        // 6. 载入 LibXposed 规范指纹拦截逻辑
        AntiLinkageXposedModule.installDirectHooks(classLoader, configFile)

        // 7. 查找主启动入口 Activity 并拉起
        var launchActivity: String? = null

        // 优先 1：查询系统 LaunchIntent
        try {
            val launchIntent = pm.getLaunchIntentForPackage(targetPackageName)
            if (launchIntent?.component != null) {
                launchActivity = launchIntent.component?.className
            }
        } catch (_: Throwable) {}

        // 优先 2：从 packageInfo.activities 中查找
        if (launchActivity == null && packageInfo?.activities != null) {
            val candidate = packageInfo.activities?.firstOrNull { act ->
                act.name.contains("Main", ignoreCase = true) ||
                act.name.contains("Launch", ignoreCase = true) ||
                act.name.contains("Home", ignoreCase = true)
            }
            launchActivity = candidate?.name ?: packageInfo.activities?.firstOrNull()?.name
        }

        // 兜底：如果是宿主自身分身，拉起 MainActivity
        if (launchActivity == null && targetPackageName == context.packageName) {
            launchActivity = "${context.packageName}.MainActivity"
        }

        if (launchActivity == null) {
            throw IllegalStateException("未在应用 [$targetPackageName] 中找到可启动的 Activity 入口！")
        }

        val intent = Intent().apply {
            setClassName(targetPackageName, launchActivity)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Log.i(TAG, "Launching virtual app [$targetPackageName] for user $userId via $launchActivity")
        context.startActivity(intent)
    }
}
