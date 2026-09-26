package com.antilinkage.sandbox.stub

import android.app.Activity
import android.os.Bundle

/**
 * 宿主在 AndroidManifest 中预埋的占坑 Activity
 * 用于规避 AMS 强校验，在到达客户端后会被替换为真实的 TargetActivity
 */
open class StubActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}

class StubActivityP0 : StubActivity()
class StubActivityP1 : StubActivity()
class StubActivityP2 : StubActivity()
