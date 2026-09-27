package com.antilinkage.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import com.antilinkage.app.databinding.DialogAppUpdateBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object AppUpdateManager {

    private const val GITHUB_REPO = "furina707/Anti-linkage-app"
    private const val RELEASES_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    data class ReleaseInfo(
        val tagName: String,
        val title: String,
        val changelog: String,
        val apkDownloadUrl: String,
        val apkSize: Long
    )

    /**
     * 检查新版本
     * @param context 上下文
     * @param isManualCheck 是否为用户手动点击检查（手动检查时若无更新会弹出 Toast 提示）
     */
    fun checkUpdate(context: Context, isManualCheck: Boolean = false) {
        thread {
            try {
                val currentVersion = getCurrentVersionName(context)
                val release = fetchLatestRelease()

                if (release == null) {
                    if (isManualCheck) {
                        (context as? Activity)?.runOnUiThread {
                            Toast.makeText(context, "未获取到版本发布信息", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return@thread
                }

                val remoteVersion = release.tagName.removePrefix("v").removePrefix("V").trim()
                val isNew = isNewerVersion(remoteVersion, currentVersion)

                (context as? Activity)?.runOnUiThread {
                    if (isNew) {
                        showUpdateDialog(context, release, currentVersion)
                    } else if (isManualCheck) {
                        Toast.makeText(context, "当前已是最新版本 (v$currentVersion)", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("AppUpdateManager", "检查更新失败", e)
                if (isManualCheck) {
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "检查更新失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo? {
        val url = URL(RELEASES_API)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "AntiLinkage-Android-App")
        }

        if (conn.responseCode != 200) return null
        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        val json = Gson().fromJson(responseText, JsonObject::class.java)

        val tagName = json.get("tag_name")?.asString ?: return null
        val title = json.get("name")?.asString ?: tagName
        val body = json.get("body")?.asString ?: "暂无更新日志"

        val assets = json.getAsJsonArray("assets") ?: JsonArray()
        var apkUrl: String? = null
        var apkSize = 0L

        // 优先选取包含 debug 的 apk，其次选取任意 apk
        for (i in 0 until assets.size()) {
            val asset = assets[i].asJsonObject
            val name = asset.get("name")?.asString ?: ""
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.get("browser_download_url")?.asString
                apkSize = asset.get("size")?.asLong ?: 0L
                if (name.contains("debug", ignoreCase = true)) {
                    break
                }
            }
        }

        if (apkUrl == null) return null

        return ReleaseInfo(
            tagName = tagName,
            title = title,
            changelog = body,
            apkDownloadUrl = apkUrl,
            apkSize = apkSize
        )
    }

    private fun showUpdateDialog(context: Context, release: ReleaseInfo, currentVersion: String) {
        val dialog = BottomSheetDialog(context)
        val binding = DialogAppUpdateBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        dialog.setCancelable(true)

        binding.tvUpdateTitle.text = release.title
        binding.tvNewVersionTag.text = release.tagName
        binding.tvCurrentVersion.text = "当前安装版本: v$currentVersion"
        binding.tvChangelog.text = release.changelog

        binding.btnCancelUpdate.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnStartUpdate.setOnClickListener {
            // 开始下载
            binding.btnStartUpdate.isEnabled = false
            binding.btnCancelUpdate.isEnabled = false
            binding.downloadProgressBar.visibility = View.VISIBLE
            binding.tvDownloadStatus.visibility = View.VISIBLE
            binding.tvDownloadStatus.text = "正在连接 GitHub 下载更新包..."

            thread {
                downloadAndInstallApk(
                    context = context,
                    downloadUrl = release.apkDownloadUrl,
                    fileName = "AntiLinkage_${release.tagName}.apk",
                    onProgress = { progress, downloadedMb, totalMb ->
                        (context as? Activity)?.runOnUiThread {
                            binding.downloadProgressBar.progress = progress
                            binding.tvDownloadStatus.text = "正在下载: $progress% ($downloadedMb MB / $totalMb MB)"
                        }
                    },
                    onComplete = { apkFile ->
                        (context as? Activity)?.runOnUiThread {
                            dialog.dismiss()
                            installApk(context, apkFile)
                        }
                    },
                    onError = { err ->
                        (context as? Activity)?.runOnUiThread {
                            binding.btnStartUpdate.isEnabled = true
                            binding.btnCancelUpdate.isEnabled = true
                            binding.tvDownloadStatus.text = "下载失败: $err"
                            Toast.makeText(context, "下载更新失败: $err", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        dialog.show()
    }

    private fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        fileName: String,
        onProgress: (progress: Int, downloadedMb: String, totalMb: String) -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val downloadDir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
            val destFile = File(downloadDir, fileName)
            if (destFile.exists()) destFile.delete()

            val url = URL(downloadUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "AntiLinkage-Android-App")
            }

            // 处理 HTTP 302 重定向
            var realConn = conn
            if (conn.responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                conn.responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                conn.responseCode == 307 || conn.responseCode == 308
            ) {
                val redirectUrl = conn.getHeaderField("Location")
                if (redirectUrl != null) {
                    realConn = (URL(redirectUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                        setRequestProperty("User-Agent", "AntiLinkage-Android-App")
                    }
                }
            }

            val totalBytes = realConn.contentLength.toLong()
            var downloadedBytes = 0L

            realConn.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var lastUpdate = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 200 || downloadedBytes == totalBytes) {
                            lastUpdate = now
                            val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                            val dlMb = String.format("%.2f", downloadedBytes / (1024f * 1024f))
                            val totMb = String.format("%.2f", totalBytes / (1024f * 1024f))
                            onProgress(progress, dlMb, totMb)
                        }
                    }
                }
            }

            AppLogger.i("AppUpdateManager", "APK downloaded successfully: ${destFile.absolutePath}")
            onComplete(destFile)
        } catch (e: Exception) {
            AppLogger.e("AppUpdateManager", "Failed to download update APK", e)
            onError(e.message ?: "未知下载错误")
        }
    }

    /**
     * 调用系统 PackageInstaller 安装 APK
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLogger.e("AppUpdateManager", "Failed to start install intent", e)
            Toast.makeText(context, "唤起系统安装器失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    /**
     * 语义化版本比对：remote 是否大于 local
     */
    private fun isNewerVersion(remote: String, local: String): Boolean {
        try {
            val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
            val lParts = local.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(rParts.size, lParts.size)
            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val l = lParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
        } catch (_: Exception) {}
        return false
    }
}
