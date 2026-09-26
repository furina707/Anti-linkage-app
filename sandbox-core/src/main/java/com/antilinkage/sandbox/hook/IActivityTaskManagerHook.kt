package com.antilinkage.sandbox.hook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
                val targetComponent = rawIntent.component

                if (targetComponent != null && targetComponent.packageName == targetPackageName) {
                    Log.i(TAG, "Intercepted startActivity: ${targetComponent.className}")

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
