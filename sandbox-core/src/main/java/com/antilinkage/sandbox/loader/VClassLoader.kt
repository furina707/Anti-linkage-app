package com.antilinkage.sandbox.loader

import dalvik.system.PathClassLoader
import java.io.File

/**
 * 虚拟应用专用类加载器
 * 负责解析目标 APK 的 Dex 字节码与 Native So 库路径
 */
class VClassLoader(
    dexPath: String,
    nativeLibraryDir: String,
    parent: ClassLoader
) : PathClassLoader(dexPath, nativeLibraryDir, parent) {

    companion object {
        fun create(apkFile: File, nativeDir: File, parent: ClassLoader): VClassLoader {
            if (!nativeDir.exists()) {
                nativeDir.mkdirs()
            }
            return VClassLoader(
                apkFile.absolutePath,
                nativeDir.absolutePath,
                parent
            )
        }
    }
}
