package com.antilinkage.fingerprint

import android.os.Build
import android.util.Log
import com.antilinkage.fingerprint.config.FingerprintManager
import com.antilinkage.fingerprint.hookers.AndroidIdHooker
import com.antilinkage.fingerprint.hookers.BuildPropHooker
import com.antilinkage.fingerprint.hookers.LocationHooker
import com.antilinkage.fingerprint.hookers.NetworkMacHooker
import com.antilinkage.fingerprint.hookers.TelephonyHooker
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.io.File

/**
 * 现代 LibXposed 规范主模块
 * 在系统级 LSPosed 宿主或用户态虚拟沙箱内装载时激活
 */
class AntiLinkageXposedModule : XposedModule() {

    companion object {
        private const val TAG = "AntiLinkageModule"

        /**
         * 提供给用户态虚拟沙箱直接调用的免 LSPosed 注入入口
         */
        fun installDirectHooks(classLoader: ClassLoader, configFile: File?) {
            Log.i(TAG, "Initializing AntiLinkage direct hooks in sandbox container...")
            if (configFile != null && configFile.exists()) {
                FingerprintManager.loadFromFile(configFile)
            }
            // 覆盖 Build 静态字段
            BuildPropHooker.applyInMemoryBuildProperties()
        }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        Log.i(TAG, "AntiLinkage module attached to target package: ${param.packageName}")

        try {
            // 1. 拦截内存中的 Build 硬件属性
            BuildPropHooker.applyInMemoryBuildProperties()

            // 2. 挂钩 Android ID 查询
            AndroidIdHooker.getTargetMethod()?.let { method ->
                hook(method).intercept { chain ->
                    AndroidIdHooker.handleIntercept(chain.args) {
                        chain.proceed()
                    }
                }
            }

            // 3. 挂钩 SystemProperties.get()
            BuildPropHooker.getSystemPropertiesMethod()?.let { method ->
                hook(method).intercept { chain ->
                    val key = chain.args.getOrNull(0) as? String ?: ""
                    val def = chain.args.getOrNull(1) as? String
                    BuildPropHooker.handleSystemPropertiesIntercept(key, def) {
                        chain.proceed()
                    }
                }
            }

            // 4. 挂钩 Telephony (IMEI / MEID / IMSI)
            for (method in TelephonyHooker.getTargetMethods()) {
                try {
                    hook(method).intercept { chain ->
                        TelephonyHooker.handleIntercept(method.name) {
                            chain.proceed()
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to hook Telephony method: ${method.name}")
                }
            }

            // 5. 挂钩 WiFi MAC 与 网卡硬件地址
            NetworkMacHooker.getWifiInfoMacMethod()?.let { method ->
                hook(method).intercept { chain ->
                    NetworkMacHooker.handleWifiInfoMac {
                        chain.proceed()
                    }
                }
            }

            NetworkMacHooker.getNetworkInterfaceMacMethod()?.let { method ->
                hook(method).intercept { chain ->
                    NetworkMacHooker.handleHardwareAddress {
                        chain.proceed()
                    }
                }
            }

            // 6. 挂钩地理位置定位
            LocationHooker.getTargetMethod()?.let { method ->
                hook(method).intercept { chain ->
                    val provider = chain.args.getOrNull(0) as? String
                    LocationHooker.handleIntercept(provider) {
                        chain.proceed()
                    }
                }
            }

            Log.i(TAG, "AntiLinkage hooks successfully installed for ${param.packageName}")

        } catch (t: Throwable) {
            Log.e(TAG, "Error installing AntiLinkage hooks", t)
        }
    }
}
