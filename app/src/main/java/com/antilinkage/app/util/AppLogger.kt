package com.antilinkage.app.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

object AppLogger {
    private const val TAG = "AntiLinkageLogger"
    private const val MAX_LOG_LINES = 300
    private val memoryLogs = ConcurrentLinkedQueue<String>()

    fun init(context: Context) {
        log("AppLogger initialized on ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        record("DEBUG", tag, msg)
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        record("INFO", tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        Log.w(tag, msg, tr)
        record("WARN", tag, "$msg ${tr?.stackTraceToString() ?: ""}")
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        Log.e(tag, msg, tr)
        record("ERROR", tag, "$msg\n${tr?.stackTraceToString() ?: ""}")
    }

    fun log(msg: String) {
        Log.i(TAG, msg)
        record("INFO", TAG, msg)
    }

    private fun record(level: String, tag: String, msg: String) {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val entry = "[$time][$level][$tag] $msg"
        memoryLogs.add(entry)
        while (memoryLogs.size > MAX_LOG_LINES) {
            memoryLogs.poll()
        }
    }

    fun getRecentLogs(): String {
        return memoryLogs.joinToString("\n")
    }

    /**
     * 捕获系统 logcat 输出与内部诊断日志
     */
    fun buildFullDiagnosticReport(context: Context, errorTitle: String? = null, exception: Throwable? = null): String {
        val sb = StringBuilder()
        sb.append("## 📱 设备环境信息\n")
        sb.append("- **应用包名**: `${context.packageName}`\n")
        sb.append("- **设备品牌/型号**: `${Build.MANUFACTURER} ${Build.MODEL}` (${Build.DEVICE})\n")
        sb.append("- **Android 版本**: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        sb.append("- **CPU 架构 (ABIs)**: `${Build.SUPPORTED_ABIS.joinToString(", ")}`\n")
        sb.append("- **时间**: `${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}`\n\n")

        if (!errorTitle.isNullOrBlank()) {
            sb.append("## ❌ 错误摘要\n")
            sb.append("`$errorTitle`\n\n")
        }

        if (exception != null) {
            sb.append("## 💥 异常堆栈 (Stack Trace)\n```\n")
            sb.append(exception.stackTraceToString())
            sb.append("\n```\n\n")
        }

        sb.append("## 📝 内部运行日志 (App In-Memory Logs)\n```\n")
        sb.append(getRecentLogs())
        sb.append("\n```\n\n")

        // 尝试抓取系统 logcat 输出
        sb.append("## 📋 系统 Logcat 诊断日志 (最近关键日志)\n```\n")
        sb.append(captureLogcat())
        sb.append("\n```\n")

        return sb.toString()
    }

    private fun captureLogcat(): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", "100"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val logLines = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                // 仅筛选相关标签或错误
                if (line?.contains("VCore") == true ||
                    line?.contains("AntiLinkage") == true ||
                    line?.contains("ActivityThread") == true ||
                    line?.contains("AndroidRuntime") == true ||
                    line?.contains("Exception") == true ||
                    line?.contains("FATAL") == true
                ) {
                    logLines.append(line).append("\n")
                }
            }
            if (logLines.isEmpty()) "（暂无相关 Logcat 日志）" else logLines.toString()
        } catch (e: Exception) {
            "读取 Logcat 失败: ${e.message}"
        }
    }

    fun saveReportToFile(context: Context, report: String): File {
        val dir = File(context.cacheDir, "crash_reports").apply { mkdirs() }
        val file = File(dir, "report_${System.currentTimeMillis()}.md")
        file.writeText(report)
        return file
    }
}
