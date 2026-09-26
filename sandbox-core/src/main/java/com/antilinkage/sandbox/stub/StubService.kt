package com.antilinkage.sandbox.stub

import android.app.Service
import android.content.Intent
import android.os.IBinder

open class StubService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}

class StubServiceP0 : StubService()
class StubServiceP1 : StubService()
