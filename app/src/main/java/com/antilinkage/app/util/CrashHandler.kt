package com.antilinkage.app.util

import android.content.Context
import android.os.Process
import java.io.File
import kotlin.system.exitProcess

class CrashHandler private constructor(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    companion object {
        fun init(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            AppLogger.e("CrashHandler", "Uncaught exception on thread [${thread.name}]", throwable)
            val report = AppLogger.buildFullDiagnosticReport(
                context = context,
                errorTitle = "应用发生未捕获崩溃: ${throwable.javaClass.simpleName} - ${throwable.message}",
                exception = throwable
            )

            // 持久化保存到本地文件
            val file = AppLogger.saveReportToFile(context, report)

            // 如果配置了 GitHub Token 且开启了自动上传，尝试后台上传
            if (GitHubLogUploader.isAutoUploadEnabled(context)) {
                val token = GitHubLogUploader.getGitHubToken(context)
                if (token.isNotBlank()) {
                    // 同步阻塞上传一小会儿（防止应用进程退出过快导致中断）
                    val thread = Thread {
                        GitHubLogUploader.uploadLog(
                            context = context,
                            title = "应用崩溃: ${throwable.javaClass.simpleName}",
                            body = report
                        ) { _, _, _ -> }
                    }
                    thread.start()
                    thread.join(2500)
                }
            }
        } catch (_: Throwable) {
        } finally {
            defaultHandler?.uncaughtException(thread, throwable) ?: run {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }
}
