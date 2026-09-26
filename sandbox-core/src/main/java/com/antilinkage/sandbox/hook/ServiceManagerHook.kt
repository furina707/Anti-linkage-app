package com.antilinkage.sandbox.hook

import android.os.IBinder
import android.util.Log
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * ServiceManager 缓存拦截器
 * 用于接管系统的 Binder 通信并注入虚拟化代理
 */
object ServiceManagerHook {
    private const val TAG = "ServiceManagerHook"

    @Suppress("UNCHECKED_CAST")
    fun hookService(serviceName: String, binderInterfaceName: String, handler: InvocationHandler) {
        try {
            val serviceManagerClz = Class.forName("android.os.ServiceManager")
            val sCacheField: Field = serviceManagerClz.getDeclaredField("sCache")
            sCacheField.isAccessible = true
            val sCache = sCacheField.get(null) as? MutableMap<String, IBinder> ?: return

            // 原始 Binder
            var rawBinder = sCache[serviceName]
            if (rawBinder == null) {
                val getServiceMethod: Method = serviceManagerClz.getDeclaredMethod("getService", String::class.java)
                rawBinder = getServiceMethod.invoke(null, serviceName) as? IBinder
            }
            if (rawBinder == null) {
                Log.w(TAG, "Cannot find raw IBinder for service: $serviceName")
                return
            }

            val targetInterface = Class.forName(binderInterfaceName)

            // 创建 IBinder 代理
            val proxyBinder = Proxy.newProxyInstance(
                rawBinder.javaClass.classLoader,
                arrayOf(IBinder::class.java),
                object : InvocationHandler {
                    override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
                        if ("queryLocalInterface" == method.name) {
                            // 当调用者尝试转换为 AIDL 接口实例时，返回我们定制的动态代理
                            return Proxy.newProxyInstance(
                                targetInterface.classLoader,
                                arrayOf(targetInterface),
                                handler
                            )
                        }
                        return if (args == null) {
                            method.invoke(rawBinder)
                        } else {
                            method.invoke(rawBinder, *args)
                        }
                    }
                }
            ) as IBinder

            // 写回 ServiceManager 的缓存
            sCache[serviceName] = proxyBinder
            Log.i(TAG, "Successfully hooked service: $serviceName -> $binderInterfaceName")

        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook service: $serviceName", t)
        }
    }
}
