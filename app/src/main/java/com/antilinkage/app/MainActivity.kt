package com.antilinkage.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.antilinkage.app.databinding.ActivityMainBinding
import com.antilinkage.app.model.ClonedAppInfo
import com.antilinkage.app.ui.ClonedAppAdapter
import com.antilinkage.app.ui.ProfileEditDialog
import com.antilinkage.fingerprint.config.InstanceFingerprint
import com.antilinkage.sandbox.VCore
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val instances = mutableListOf<ClonedAppInfo>()
    private lateinit var adapter: ClonedAppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        setupListeners()

        // 默认初始化生成两个示范分身实例
        createInitialSampleInstances()
    }

    private fun setupRecyclerView() {
        adapter = ClonedAppAdapter(
            instances = instances,
            onLaunchClick = { item ->
                launchInstance(item)
            },
            onConfigClick = { item ->
                ProfileEditDialog.show(this, item.fingerprint) { updated ->
                    item.fingerprint = updated
                    val index = instances.indexOf(item)
                    if (index != -1) {
                        adapter.notifyItemChanged(index)
                    }
                    Toast.makeText(this, "分身 #${item.userId} 独立指纹配置已更新", Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.rvInstances.layoutManager = LinearLayoutManager(this)
        binding.rvInstances.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnAddInstance.setOnClickListener {
            val newUserId = if (instances.isEmpty()) 0 else instances.maxOf { it.userId } + 1
            val sampleApk = File(applicationInfo.sourceDir)

            val newInstance = ClonedAppInfo(
                userId = newUserId,
                appName = "目标应用",
                packageName = "com.target.sampleapp",
                apkFile = sampleApk,
                fingerprint = InstanceFingerprint(
                    userId = newUserId,
                    packageName = "com.target.sampleapp",
                    androidId = InstanceFingerprint.generateRandomHex(16),
                    imei = InstanceFingerprint.generateRandomNumeric(15),
                    macAddress = InstanceFingerprint.generateRandomMac(),
                    brand = listOf("Google", "Samsung", "Xiaomi").random(),
                    model = listOf("Pixel 7", "Galaxy S23", "Mi 13").random()
                )
            )

            instances.add(newInstance)
            adapter.notifyItemInserted(instances.size - 1)
            binding.rvInstances.smoothScrollToPosition(instances.size - 1)
            Toast.makeText(this, "已创建新分身 #${newUserId}，已分配全新硬件指纹", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createInitialSampleInstances() {
        val sampleApk = File(applicationInfo.sourceDir)
        instances.add(
            ClonedAppInfo(
                userId = 0,
                appName = "电商 / 社交应用",
                packageName = "com.target.sampleapp",
                apkFile = sampleApk,
                fingerprint = InstanceFingerprint(
                    userId = 0,
                    packageName = "com.target.sampleapp",
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
                appName = "电商 / 社交应用",
                packageName = "com.target.sampleapp",
                apkFile = sampleApk,
                fingerprint = InstanceFingerprint(
                    userId = 1,
                    packageName = "com.target.sampleapp",
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
        Toast.makeText(
            this,
            "正在通过沙箱拉起分身 #${item.userId} (Android ID: ${item.fingerprint.androidId})",
            Toast.LENGTH_LONG
        ).show()

        try {
            VCore.launchVirtualApp(
                context = this,
                targetApk = item.apkFile,
                userId = item.userId,
                customFingerprint = item.fingerprint
            )
        } catch (e: Exception) {
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
