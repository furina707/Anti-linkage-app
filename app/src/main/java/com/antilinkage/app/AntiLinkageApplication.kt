package com.antilinkage.app

import android.app.Application
import android.util.Log
import com.antilinkage.sandbox.VCore

/**
 * 宿主应用程序入口
 */
class AntiLinkageApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("AntiLinkageApp", "Application starting, initializing virtualization core...")
        // 预热并初始化用户态沙箱底座与 Hidden API 豁免
        VCore.init(this)
    }
}
