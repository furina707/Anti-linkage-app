package com.antilinkage.sandbox.vfs

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 虚拟文件系统管理器
 * 负责分身实例私有目录的物理创建与路径映射
 */
object VFileSystem {
    private const val TAG = "VFileSystem"

    init {
        try {
            System.loadLibrary("vfs_core")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load vfs_core native library", e)
        }
    }

    external fun nativeInitVFS(hostPkg: String, targetPkg: String, userId: Int): Boolean
    external fun nativeRedirectPath(rawPath: String): String

    /**
     * 为指定分身实例准备隔离的文件环境
     * /data/data/<host>/virtual/users/<userId>/<targetPkg>/
     * ├── files
     * ├── databases
     * ├── shared_prefs
     * ├── cache
     * └── fingerprint.json
     */
    fun initUserEnvironment(context: Context, targetPkg: String, userId: Int): File {
        val virtualRoot = File(context.filesDir.parentFile, "virtual/users/$userId/$targetPkg")
        if (!virtualRoot.exists()) {
            virtualRoot.mkdirs()
        }

        File(virtualRoot, "files").mkdirs()
        File(virtualRoot, "databases").mkdirs()
        File(virtualRoot, "shared_prefs").mkdirs()
        File(virtualRoot, "cache").mkdirs()

        // 初始化 Native 层重定向规则
        try {
            nativeInitVFS(context.packageName, targetPkg, userId)
        } catch (t: Throwable) {
            Log.w(TAG, "Native VFS init error", t)
        }

        Log.i(TAG, "User $userId environment initialized at: ${virtualRoot.absolutePath}")
        return virtualRoot
    }

    /**
     * 获取指定分身的数据存储目录
     */
    fun getUserDataDir(context: Context, targetPkg: String, userId: Int): File {
        return File(context.filesDir.parentFile, "virtual/users/$userId/$targetPkg")
    }

    /**
     * 获取指定分身的指纹配置文件
     */
    fun getFingerprintConfigFile(context: Context, targetPkg: String, userId: Int): File {
        return File(getUserDataDir(context, targetPkg, userId), "fingerprint.json")
    }
}
