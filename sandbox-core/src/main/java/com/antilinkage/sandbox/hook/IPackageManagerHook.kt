package com.antilinkage.sandbox.hook

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

/**
 * 虚拟化 PackageManager 动态代理
 * 拦截 PMS 调用，向目标应用提供其自身的 PackageInfo 与 ApplicationInfo
 */
class IPackageManagerHook(
    private val context: Context,
    private val targetPackageName: String,
    private val realPackageInfo: PackageInfo?
) : InvocationHandler {

    companion object {
        private const val TAG = "IPackageManagerHook"

        fun install(context: Context, targetPkg: String, info: PackageInfo?) {
            val handler = IPackageManagerHook(context, targetPkg, info)
            ServiceManagerHook.hookService(
                "package",
                "android.content.pm.IPackageManager",
                handler
            )
        }
    }

    override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
        val methodName = method.name

        // 拦截获取应用包信息
        if (methodName == "getPackageInfo") {
            val requestedPkg = args?.getOrNull(0) as? String
            if (requestedPkg == targetPackageName && realPackageInfo != null) {
                Log.d(TAG, "Intercepted getPackageInfo for: $targetPackageName")
                return realPackageInfo
            }
        }

        // 拦截获取 ApplicationInfo
        if (methodName == "getApplicationInfo") {
            val requestedPkg = args?.getOrNull(0) as? String
            if (requestedPkg == targetPackageName && realPackageInfo != null) {
                Log.d(TAG, "Intercepted getApplicationInfo for: $targetPackageName")
                return realPackageInfo.applicationInfo
            }
        }

        // 默认回退到宿主真实 PMS
        return try {
            val pm = context.packageManager
            val mPmField = pm.javaClass.getDeclaredField("mPM")
            mPmField.isAccessible = true
            val realIPm = mPmField.get(pm)
            if (args == null) {
                method.invoke(realIPm)
            } else {
                method.invoke(realIPm, *args)
            }
        } catch (t: Throwable) {
            null
        }
    }
}
