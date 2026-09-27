package com.antilinkage.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.antilinkage.app.databinding.ActivityMainBinding
import com.antilinkage.app.model.ClonedAppInfo
import com.antilinkage.app.ui.AppPickerDialog
import com.antilinkage.app.ui.ClonedAppAdapter
import com.antilinkage.app.ui.LogDiagnosticsDialog
import com.antilinkage.app.ui.ProfileEditDialog
import com.antilinkage.app.util.AppIconHelper
import com.antilinkage.app.util.AppLogger
import com.antilinkage.fingerprint.config.InstanceFingerprint
import com.antilinkage.sandbox.VCore
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val instances = mutableListOf<ClonedAppInfo>()
    private lateinit var adapter: ClonedAppAdapter
    private var isGridMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        setupListeners()

        // 默认初始化生成示范分身实例
        createInitialSampleInstances()

        // 启动时后台静默检查是否有 GitHub 新版本发布
        com.antilinkage.app.util.AppUpdateManager.checkUpdate(this, isManualCheck = false)
    }

    private fun setupRecyclerView() {
        adapter = ClonedAppAdapter(
            instances = instances,
            isGridMode = isGridMode,
            onLaunchClick = { item ->
                launchInstance(item)
            },
            onConfigClick = { item ->
                editFingerprint(item)
            },
            onOptionsClick = { item, _ ->
                showInstanceActionDialog(item)
            }
        )
        updateLayoutManager()
        binding.rvInstances.adapter = adapter
    }

    private fun updateLayoutManager() {
        if (isGridMode) {
            binding.rvInstances.layoutManager = GridLayoutManager(this, 3)
            binding.btnToggleView.text = "切换列表"
        } else {
            binding.rvInstances.layoutManager = LinearLayoutManager(this)
            binding.btnToggleView.text = "切换图标"
        }
        adapter.isGridMode = isGridMode
        adapter.notifyDataSetChanged()
    }

    private fun setupListeners() {
        binding.btnToggleView.setOnClickListener {
            isGridMode = !isGridMode
            updateLayoutManager()
        }

        binding.btnLogs.setOnClickListener {
            LogDiagnosticsDialog.show(this, "用户主动查看运行日志与诊断", null, false)
        }

        binding.btnCheckUpdate.setOnClickListener {
            com.antilinkage.app.util.AppUpdateManager.checkUpdate(this, isManualCheck = true)
        }

        binding.btnAddInstance.setOnClickListener {
            // 弹出应用选择器，支持从手机已安装的所有应用中直接挑选多开目标
            AppPickerDialog.show(this) { appName, packageName, apkFile ->
                val newUserId = if (instances.isEmpty()) 0 else instances.maxOf { it.userId } + 1
                val newInstance = ClonedAppInfo(
                    userId = newUserId,
                    appName = appName,
                    packageName = packageName,
                    apkFile = apkFile,
                    fingerprint = InstanceFingerprint(
                        userId = newUserId,
                        packageName = packageName,
                        androidId = InstanceFingerprint.generateRandomHex(16),
                        imei = InstanceFingerprint.generateRandomNumeric(15),
                        macAddress = InstanceFingerprint.generateRandomMac(),
                        brand = listOf("Google", "Samsung", "Xiaomi", "OPPO", "vivo").random(),
                        model = listOf("Pixel 8", "Galaxy S24", "Mi 14", "Find X7", "X100").random()
                    )
                )

                instances.add(newInstance)
                adapter.notifyItemInserted(instances.size - 1)
                binding.rvInstances.smoothScrollToPosition(instances.size - 1)
                AppLogger.i("MainActivity", "创建新分身: $appName ($packageName, id=$newUserId)")
                Toast.makeText(this, "已为 [${appName}] 创建分身 #${newUserId}，已分配全新硬件指纹", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showInstanceActionDialog(item: ClonedAppInfo) {
        val options = arrayOf(
            "🚀 立即启动分身",
            "⚙️ 配置独立指纹 (Android ID/IMEI/MAC/GPS)",
            "ℹ️ 查看当前隔离指纹参数",
            "📋 查看诊断日志 / 上报 GitHub",
            "🗑️ 删除此分身"
        )

        AlertDialog.Builder(this)
            .setTitle("${item.appName} (分身 #${item.userId})")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchInstance(item)
                    1 -> editFingerprint(item)
                    2 -> showFingerprintDetails(item)
                    3 -> LogDiagnosticsDialog.show(this, "分身 [${item.appName}] 诊断排查", null, false)
                    4 -> deleteInstance(item)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun editFingerprint(item: ClonedAppInfo) {
        ProfileEditDialog.show(this, item.fingerprint) { updated ->
            item.fingerprint = updated
            val index = instances.indexOf(item)
            if (index != -1) {
                adapter.notifyItemChanged(index)
            }
            AppLogger.i("MainActivity", "更新分身 #${item.userId} 指纹: ${updated.brand} ${updated.model}")
            Toast.makeText(this, "分身 #${item.userId} 独立指纹配置已更新", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFingerprintDetails(item: ClonedAppInfo) {
        val fp = item.fingerprint
        val message = """
            品牌: ${fp.brand}
            型号: ${fp.model}
            Android ID: ${fp.androidId}
            IMEI: ${fp.imei}
            MAC: ${fp.macAddress}
            虚拟定位: ${fp.latitude}, ${fp.longitude}
            目标 APK: ${item.apkFile.absolutePath}
            沙箱隔离路径:
            /data/data/${packageName}/virtual/users/${item.userId}/${item.packageName}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("分身 #${item.userId} 硬件隔离指纹详情")
            .setMessage(message)
            .setPositiveButton("修改") { _, _ -> editFingerprint(item) }
            .setNegativeButton("确定", null)
            .show()
    }

    private fun deleteInstance(item: ClonedAppInfo) {
        val index = instances.indexOf(item)
        if (index != -1) {
            instances.removeAt(index)
            adapter.notifyItemRemoved(index)
            AppLogger.i("MainActivity", "删除分身 #${item.userId}")
            Toast.makeText(this, "分身 #${item.userId} 已删除", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createInitialSampleInstances() {
        val sampleApk = File(applicationInfo.sourceDir)
        val defaultName = AppIconHelper.getAppLabel(this, sampleApk, getString(R.string.app_name))

        instances.add(
            ClonedAppInfo(
                userId = 0,
                appName = defaultName,
                packageName = packageName,
                apkFile = sampleApk,
                fingerprint = InstanceFingerprint(
                    userId = 0,
                    packageName = packageName,
                    brand = "Google",
                    model = "Pixel 7 Pro",
                    androidId = "a1b2c3d4e5f67890",
                    imei = "862345051234567",
                    macAddress = "00:1A:2B:3C:4D:5E",
                    latitude = 39.9042,
                    longitude = 116.4074
                )
            )
        )
        instances.add(
            ClonedAppInfo(
                userId = 1,
                appName = defaultName,
                packageName = packageName,
                apkFile = sampleApk,
                fingerprint = InstanceFingerprint(
                    userId = 1,
                    packageName = packageName,
                    brand = "Samsung",
                    model = "Galaxy S23 Ultra",
                    androidId = "f0e1d2c3b4a59687",
                    imei = "354789098765432",
                    macAddress = "E4:5F:01:A2:B3:C4",
                    latitude = 31.2304,
                    longitude = 121.4737
                )
            )
        )
        adapter.notifyDataSetChanged()
    }

    private fun launchInstance(item: ClonedAppInfo) {
        AppLogger.i("MainActivity", "准备拉起分身 #${item.userId} [${item.appName}] (${item.packageName})")
        Toast.makeText(
            this,
            "正在通过沙箱拉起分身 #${item.userId} (Android ID: ${item.fingerprint.androidId})",
            Toast.LENGTH_SHORT
        ).show()

        try {
            VCore.launchVirtualApp(
                context = this,
                targetApk = item.apkFile,
                userId = item.userId,
                customFingerprint = item.fingerprint
            )
            AppLogger.i("MainActivity", "分身 #${item.userId} 拉起指令已发送")
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "分身 #${item.userId} 启动异常: ${e.message}", e)
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()

            // 弹出诊断与自动上传 GitHub 对话框
            LogDiagnosticsDialog.show(
                context = this,
                errorTitle = "分身 [${item.appName}] 启动失败: ${e.message}",
                exception = e,
                autoTriggerUpload = true
            )
        }
    }
}
