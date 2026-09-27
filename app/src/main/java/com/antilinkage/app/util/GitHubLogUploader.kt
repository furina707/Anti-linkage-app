package com.antilinkage.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

object GitHubLogUploader {

    private const val PREFS_NAME = "github_log_prefs"
    private const val KEY_GITHUB_TOKEN = "github_token"
    private const val KEY_AUTO_UPLOAD = "auto_upload"
    const val DEFAULT_REPO = "furina707/Anti-linkage-app"

    fun getGitHubToken(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GITHUB_TOKEN, "") ?: ""
    }

    fun setGitHubToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GITHUB_TOKEN, token.trim())
            .apply()
    }

    fun isAutoUploadEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_UPLOAD, true)
    }

    fun setAutoUploadEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_UPLOAD, enabled)
            .apply()
    }

    /**
     * 上传日志到 GitHub Issues
     */
    fun uploadLog(
        context: Context,
        title: String,
        body: String,
        onComplete: (success: Boolean, issueUrl: String?, errorMsg: String?) -> Unit
    ) {
        val token = getGitHubToken(context)

        if (token.isBlank()) {
            onComplete(false, null, "未配置 GitHub Token，建议填写 Token 或使用免密浏览器一键提交")
            return
        }

        thread {
            try {
                val apiUrl = "https://api.github.com/repos/$DEFAULT_REPO/issues"
                val url = URL(apiUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "AntiLinkage-Android-App")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }

                val payload = JsonObject().apply {
                    addProperty("title", "[自动诊断日志] $title")
                    addProperty("body", body)
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(Gson().toJson(payload))
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == 201) {
                    val responseJson = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = Gson().fromJson(responseJson, JsonObject::class.java)
                    val htmlUrl = jsonObject.get("html_url")?.asString
                    onComplete(true, htmlUrl, null)
                } else {
                    val errResponse = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    onComplete(false, null, "GitHub API 响应失败 (HTTP $responseCode): $errResponse")
                }
            } catch (e: Exception) {
                onComplete(false, null, "网络连接或上传异常: ${e.message}")
            }
        }
    }

    /**
     * 无 Token 或上传失败时：复制日志到剪贴板并跳转至 GitHub Issues 页面
     */
    fun openGitHubNewIssueBrowser(context: Context, title: String, body: String) {
        try {
            // 复制详细诊断日志到剪贴板
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("AntiLinkage Log", body)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "诊断日志已自动复制到剪贴板！正在打开 GitHub Issue 页面...", Toast.LENGTH_SHORT).show()

            // 限制 URL 长度，其余引导用户直接从剪贴板粘贴
            val encodedTitle = URLEncoder.encode("[自动诊断日志] $title", "UTF-8")
            val truncatedBody = if (body.length > 1500) {
                body.substring(0, 1500) + "\n\n...（详细完整日志已自动复制到剪贴板，请在此处直接粘贴）..."
            } else {
                body
            }
            val encodedBody = URLEncoder.encode(truncatedBody, "UTF-8")
            val issueWebUrl = "https://github.com/$DEFAULT_REPO/issues/new?title=$encodedTitle&body=$encodedBody"

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(issueWebUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "打开浏览器失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
