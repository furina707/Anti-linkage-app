package com.antilinkage.sandbox.hook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.antilinkage.sandbox.stub.StubActivityP0
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

/**
 * 拦截 AMS / ATMS 的 startActivity 调用
 * 实施出栈“偷梁换柱”：将未注册的 TargetActivity 包装并替换为已预埋的 StubActivity
 */
class IActivityTaskManagerHook(
    private val context: Context,
    private val targetPackageName: String
) : InvocationHandler {

    companion object {
        private const val TAG = "IActivityTaskManagerHook"
        const val EXTRA_TARGET_INTENT = "_anti_linkage_target_intent_"
        const val EXTRA_BYPASS_HOOK = "_anti_linkage_bypass_hook_"

        fun install(context: Context, targetPkg: String) {
            val handler = IActivityTaskManagerHook(context, targetPkg)

            // Android 10+ 引入 activity_task (ATMS)，低版本使用 activity (AMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceManagerHook.hookService(
                    "activity_task",
                    "android.app.IActivityTaskManager",
                    handler
                )
            }
            ServiceManagerHook.hookService(
                "activity",
                "android.app.IActivityManager",
                handler
            )
        }
    }

    override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
        if ("startActivity" == method.name && args != null) {
            // 查找 Intent 参数位置
            var intentIndex = -1
            for (i in args.indices) {
                if (args[i] is Intent) {
                    intentIndex = i
                    break
                }
            }

            if (intentIndex != -1) {
                val rawIntent = args[intentIndex] as Intent

                // 如果标记了绕过 Hook 或已经是占坑组件，直接放行
                if (rawIntent.getBooleanExtra(EXTRA_BYPASS_HOOK, false)) {
                    Log.d(TAG, "Bypassing hook for resolved target intent")
                    return method.invoke(getRealService(), *args)
                }

                val targetComponent = rawIntent.component
                if (targetComponent != null && targetComponent.packageName == targetPackageName) {
                    // 检查目标组件是否在系统系统中已注册为已导出（针对已安装应用双开）
                    val isDirectlyLaunchable = try {
                        val ri = context.packageManager.resolveActivity(rawIntent, 0)
                        ri != null && (ri.activityInfo.exported || ri.activityInfo.packageName == context.packageName)
                    } catch (_: Throwable) {
                        false
                    }

                    // 如果系统本身认识这个组件且宿主能直接启动它，无需强制占坑包装
                    if (isDirectlyLaunchable && targetComponent.packageName != context.packageName) {
                        Log.i(TAG, "System recognizes target component, passing directly: ${targetComponent.className}")
                        return method.invoke(getRealService(), *args)
                    }

                    Log.i(TAG, "Wrapping unregistered target into StubActivity: ${targetComponent.className}")

                    // 构造替身 Intent，指向宿主预埋的 StubActivity
                    val stubIntent = Intent().apply {
                        component = ComponentName(context.packageName, StubActivityP0::class.java.name)
                        putExtra(EXTRA_TARGET_INTENT, rawIntent)
                    }

                    // 替换方法参数列表中的原始 Intent
                    val newArgs = Array<Any?>(args.size) { i -> if (i == intentIndex) stubIntent else args[i] }
                    return method.invoke(getRealService(), *newArgs)
                }
            }
        }

        // 其他调用放行到原始服务
        return try {
            val real = getRealService()
            if (args == null) method.invoke(real) else method.invoke(real, *args)
        } catch (t: Throwable) {
            null
        }
    }

    private fun getRealService(): Any? {
        return try {
            val activityManagerClass = Class.forName("android.app.ActivityManager")
            val getServiceMethod = activityManagerClass.getDeclaredMethod("getService")
            getServiceMethod.invoke(null)
        } catch (e: Exception) {
            null
        }
    }
}
