package com.antilinkage.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import com.antilinkage.app.databinding.DialogLogDiagnosticsBinding
import com.antilinkage.app.util.AppLogger
import com.antilinkage.app.util.GitHubLogUploader
import com.google.android.material.bottomsheet.BottomSheetDialog

object LogDiagnosticsDialog {

    fun show(
        context: Context,
        errorTitle: String? = null,
        exception: Throwable? = null,
        autoTriggerUpload: Boolean = false
    ) {
        val dialog = BottomSheetDialog(context)
        val binding = DialogLogDiagnosticsBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        val report = AppLogger.buildFullDiagnosticReport(context, errorTitle, exception)
        binding.tvLogPreview.text = report

        // 加载并监听 Token
        val savedToken = GitHubLogUploader.getGitHubToken(context)
        binding.etGitHubToken.setText(savedToken)
        binding.etGitHubToken.doAfterTextChanged {
            GitHubLogUploader.setGitHubToken(context, it?.toString() ?: "")
        }

        // 加载并监听自动上传开关
        binding.switchAutoUpload.isChecked = GitHubLogUploader.isAutoUploadEnabled(context)
        binding.switchAutoUpload.setOnCheckedChangeListener { _, isChecked ->
            GitHubLogUploader.setAutoUploadEnabled(context, isChecked)
        }

        // 复制日志
        binding.btnCopyLog.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Diagnostic Log", report))
            Toast.makeText(context, "完整诊断日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }

        // 浏览器免密提交 Issue
        binding.btnOpenWebIssue.setOnClickListener {
            GitHubLogUploader.openGitHubNewIssueBrowser(context, errorTitle ?: "沙箱启动诊断日志", report)
        }

        fun doUpload() {
            val token = GitHubLogUploader.getGitHubToken(context)
            if (token.isBlank()) {
                // 如果未配置 Token，直接唤起浏览器免密提交
                GitHubLogUploader.openGitHubNewIssueBrowser(context, errorTitle ?: "分身启动异常", report)
                return
            }

            binding.uploadProgress.visibility = View.VISIBLE
            binding.tvUploadStatus.visibility = View.VISIBLE
            binding.tvUploadStatus.text = "正在上传日志到 GitHub..."
            binding.btnUploadGitHub.isEnabled = false

            GitHubLogUploader.uploadLog(
                context = context,
                title = errorTitle ?: "分身启动异常日志",
                body = report
            ) { success, issueUrl, errorMsg ->
                binding.root.post {
                    binding.uploadProgress.visibility = View.GONE
                    binding.btnUploadGitHub.isEnabled = true

                    if (success && issueUrl != null) {
                        binding.tvUploadStatus.text = "✅ 上传成功！点击下方链接查看："
                        binding.tvUploadStatus.setOnClickListener {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                        Toast.makeText(context, "已成功创建 GitHub Issue！", Toast.LENGTH_LONG).show()
                    } else {
                        binding.tvUploadStatus.text = "❌ 上传失败: $errorMsg"
                        Toast.makeText(context, "自动上传遇到问题，可点击下方在浏览器提交", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 立即上传按钮
        binding.btnUploadGitHub.setOnClickListener {
            doUpload()
        }

        // 如果开启了自动触发上传且有 Token
        if (autoTriggerUpload && GitHubLogUploader.isAutoUploadEnabled(context) && savedToken.isNotBlank()) {
            doUpload()
        }

        dialog.show()
    }
}
