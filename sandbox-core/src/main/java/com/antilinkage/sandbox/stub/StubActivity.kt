package com.antilinkage.sandbox.stub

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.antilinkage.sandbox.hook.IActivityTaskManagerHook

/**
 * 宿主在 AndroidManifest 中预埋的占坑 Activity
 * 用于规避 AMS 强校验，在到达客户端后会被替换为真实的 TargetActivity
 * 如果底层系统 (Android 13/14) 拦截导致 ActivityThreadHook 未能及时入栈还原，
 * StubActivity 作为第二道防护兜底自动解包并拉起真实的 TargetActivity
 */
open class StubActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val targetIntent = intent?.getParcelableExtra<Intent>(IActivityTaskManagerHook.EXTRA_TARGET_INTENT)

        if (targetIntent != null && targetIntent.component != null) {
            try {
                Log.i("StubActivity", "Fallback unwrapping target intent in StubActivity: ${targetIntent.component?.className}")
                targetIntent.removeExtra(IActivityTaskManagerHook.EXTRA_TARGET_INTENT)
                targetIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                startActivity(targetIntent)
                finish()
                return
            } catch (t: Throwable) {
                Log.e("StubActivity", "Failed to forward target intent from StubActivity fallback", t)
            }
        }
    }
}

class StubActivityP0 : StubActivity()
class StubActivityP1 : StubActivity()
class StubActivityP2 : StubActivity()
