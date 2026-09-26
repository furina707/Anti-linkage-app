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

            Log.i(TAG, "ActivityThread.mH hook successfully installed.")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ActivityThread.mH", t)
        }
    }

    private fun handleMessage(msg: Message, classLoader: ClassLoader) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && msg.what == EXECUTE_TRANSACTION) {
                // Android 9.0+ ClientTransaction
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
        val mActivityCallbacksField = transaction.javaClass.getDeclaredField("mActivityCallbacks")
        mActivityCallbacksField.isAccessible = true
        val callbacks = mActivityCallbacksField.get(transaction) as? List<*> ?: return

        for (item in callbacks) {
            if (item != null && item.javaClass.name.contains("LaunchActivityItem")) {
                val mIntentField = item.javaClass.getDeclaredField("mIntent")
                mIntentField.isAccessible = true
                val stubIntent = mIntentField.get(item) as? Intent ?: continue

                stubIntent.setExtrasClassLoader(classLoader)
                val targetIntent = stubIntent.getParcelableExtra<Intent>(IActivityTaskManagerHook.EXTRA_TARGET_INTENT)

                if (targetIntent != null) {
                    Log.i(TAG, "Restoring target intent: ${targetIntent.component?.className}")
                    mIntentField.set(item, targetIntent)
                }
            }
        }
    }

    private fun handleLaunchActivity(record: Any?, classLoader: ClassLoader) {
        if (record == null) return
        val intentField = record.javaClass.getDeclaredField("intent")
        intentField.isAccessible = true
        val stubIntent = intentField.get(record) as? Intent ?: return

        stubIntent.setExtrasClassLoader(classLoader)
        val targetIntent = stubIntent.getParcelableExtra<Intent>(IActivityTaskManagerHook.EXTRA_TARGET_INTENT)

        if (targetIntent != null) {
            Log.i(TAG, "Restoring target intent (legacy): ${targetIntent.component?.className}")
            intentField.set(record, targetIntent)
        }
    }
}
