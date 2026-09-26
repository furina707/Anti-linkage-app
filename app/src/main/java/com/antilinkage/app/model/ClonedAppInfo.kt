package com.antilinkage.app.model

import com.antilinkage.fingerprint.config.InstanceFingerprint
import java.io.File
import java.io.Serializable

/**
 * 分身实例列表呈现数据模型
 */
data class ClonedAppInfo(
    val userId: Int,
    val appName: String,
    val packageName: String,
    val apkFile: File,
    var fingerprint: InstanceFingerprint
) : Serializable
