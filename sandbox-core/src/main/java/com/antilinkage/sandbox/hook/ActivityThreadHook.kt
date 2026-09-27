package com.antilinkage.sandbox.hook

import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Message
import android.util.Log
import java.lang.reflect.Field

/**
 * ActivityThread.mH 消息循环钩子
 * 在 AMS 回调 StubActivity 准备实例化之时，实施入栈“还原”：
 * 将 StubActivity 换回真正的 TargetActivity，使系统使用目标应用代码完成渲染
 * 全面兼容 Android 8.0 ~ Android 15 (API 35) 的 ClientTransaction 结构变更
 */
object ActivityThreadHook {
    private const val TAG = "ActivityThreadHook"
    private const val LAUNCH_ACTIVITY = 100
    private const val EXECUTE_TRANSACTION = 159

    fun install(classLoader: ClassLoader) {
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread")
            currentActivityThreadMethod.isAccessible = true
            val currentActivityThread = currentActivityThreadMethod.invoke(null)

            val mHField = activityThreadClass.getDeclaredField("mH")
            mHField.isAccessible = true
            val mH = mHField.get(currentActivityThread) as Handler

            val mCallbackField = Handler::class.java.getDeclaredField("mCallback")
            mCallbackField.isAccessible = true
            val rawCallback = mCallbackField.get(mH) as? Handler.Callback

            // 安装自定义 Handler.Callback
            mCallbackField.set(mH, Handler.Callback { msg ->
                handleMessage(msg, classLoader)
                rawCallback?.handleMessage(msg) ?: false
            })

            Log.i(TAG, "ActivityThread.mH hook successfully installed (Android ${Build.VERSION.SDK_INT}).")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ActivityThread.mH", t)
        }
    }

    private fun handleMessage(msg: Message, classLoader: ClassLoader) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && msg.what == EXECUTE_TRANSACTION) {
                // Android 9.0 ~ 15+ ClientTransaction
                handleExecuteTransaction(msg.obj, classLoader)
            } else if (msg.what == LAUNCH_ACTIVITY) {
                // Android 8.0/8.1 ActivityClientRecord
                handleLaunchActivity(msg.obj, classLoader)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error unwrapping target intent in mH", t)
        }
    }

    private fun handleExecuteTransaction(transaction: Any?, classLoader: ClassLoader) {
        if (transaction == null) return

        try {
            val items = findTransactionItems(transaction)
            for (item in items) {
                if (item != null) {
                    unwrapIntentInObject(item, classLoader)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed in handleExecuteTransaction", t)
        }
    }

    /**
     * 针对 Android 9 ~ 15 全面提取 ClientTransaction 中的事务项列表
     */
    private fun findTransactionItems(transaction: Any): List<Any?> {
        val list = mutableListOf<Any?>()

        // 尝试方法 1：调用 getTransactionItems() (Android 15+)
        try {
            val method = transaction.javaClass.getDeclaredMethod("getTransactionItems")
            method.isAccessible = true
            val result = method.invoke(transaction)
            if (result is List<*>) {
                list.addAll(result)
            }
        } catch (_: Throwable) {}

        // 尝试方法 2：调用 getCallbacks()
        if (list.isEmpty()) {
            try {
                val method = transaction.javaClass.getDeclaredMethod("getCallbacks")
                method.isAccessible = true
                val result = method.invoke(transaction)
                if (result is List<*>) {
                    list.addAll(result)
                }
            } catch (_: Throwable) {}
        }

        // 尝试字段 3：反射字段 mActivityCallbacks (Android 9-14) 或 mActivityTransactionItems (Android 15)
        if (list.isEmpty()) {
            for (fieldName in arrayOf("mActivityTransactionItems", "mActivityCallbacks", "mTransactionItems")) {
                try {
                    val field = transaction.javaClass.getDeclaredField(fieldName)
                    field.isAccessible = true
                    val result = field.get(transaction)
                    if (result is List<*>) {
                        list.addAll(result)
                        break
                    }
                } catch (_: Throwable) {}
            }
        }

        // 尝试生命周期状态请求项 mLifecycleStateRequest
        try {
            val lifecycleField = transaction.javaClass.getDeclaredField("mLifecycleStateRequest")
            lifecycleField.isAccessible = true
            val lifecycleItem = lifecycleField.get(transaction)
            if (lifecycleItem != null) {
                list.add(lifecycleItem)
            }
        } catch (_: Throwable) {}

        return list
    }

    /**
     * 递归遍历查找包含 EXTRA_TARGET_INTENT 的 Intent 并实施原地还原
     */
    private fun unwrapIntentInObject(item: Any, classLoader: ClassLoader) {
        var currentClass: Class<*>? = item.javaClass
        while (currentClass != null && currentClass != Any::class.java) {
            for (field in currentClass.declaredFields) {
                if (Intent::class.java.isAssignableFrom(field.type)) {
                    field.isAccessible = true
                    val stubIntent = field.get(item) as? Intent ?: continue
                    stubIntent.setExtrasClassLoader(classLoader)

                    val targetIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        stubIntent.getParcelableExtra(IActivityTaskManagerHook.EXTRA_TARGET_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        stubIntent.getParcelableExtra<Intent>(IActivityTaskManagerHook.EXTRA_TARGET_INTENT)
                    }

                    if (targetIntent != null) {
                        Log.i(TAG, "Successfully restored target intent for ${targetIntent.component?.className} in ${item.javaClass.simpleName}.${field.name}")
                        field.set(item, targetIntent)
                        return
                    }
                }
            }
            currentClass = currentClass.superclass
        }
    }

    private fun handleLaunchActivity(record: Any?, classLoader: ClassLoader) {
        if (record == null) return
        try {
            val intentField = record.javaClass.getDeclaredField("intent")
            intentField.isAccessible = true
            val stubIntent = intentField.get(record) as? Intent ?: return

            stubIntent.setExtrasClassLoader(classLoader)
            val targetIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                stubIntent.getParcelableExtra(IActivityTaskManagerHook.EXTRA_TARGET_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                stubIntent.getParcelableExtra<Intent>(IActivityTaskManagerHook.EXTRA_TARGET_INTENT)
            }

            if (targetIntent != null) {
                Log.i(TAG, "Restoring target intent (legacy Android 8): ${targetIntent.component?.className}")
                intentField.set(record, targetIntent)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error in handleLaunchActivity", t)
        }
    }
}
