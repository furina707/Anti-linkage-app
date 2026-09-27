package com.antilinkage.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.antilinkage.app.databinding.DialogSelectAppBinding
import com.antilinkage.app.databinding.ItemAppToCloneBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import kotlin.concurrent.thread

data class InstalledAppItem(
    val name: String,
    val packageName: String,
    val apkFile: File,
    val icon: Drawable
)

object AppPickerDialog {

    fun show(
        context: Context,
        onAppSelected: (appName: String, packageName: String, apkFile: File) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val binding = DialogSelectAppBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        binding.rvApps.layoutManager = LinearLayoutManager(context)

        thread {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val launchableList = pm.queryIntentActivities(intent, 0)
            val seenPackages = mutableSetOf<String>()
            val appItems = mutableListOf<InstalledAppItem>()

            for (resolveInfo in launchableList) {
                val pkg = resolveInfo.activityInfo.packageName
                if (seenPackages.contains(pkg)) continue
                seenPackages.add(pkg)

                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = resolveInfo.loadLabel(pm).toString()
                    val icon = resolveInfo.loadIcon(pm)
                    val apkFile = File(appInfo.sourceDir)
                    if (apkFile.exists()) {
                        appItems.add(InstalledAppItem(label, pkg, apkFile, icon))
                    }
                } catch (_: Exception) {}
            }

            // 按首字母升序排序，自己的应用放在最前
            appItems.sortBy { if (it.packageName == context.packageName) "" else it.name }

            binding.root.post {
                binding.progressBar.visibility = View.GONE
                binding.rvApps.adapter = AppAdapter(appItems) { item ->
                    dialog.dismiss()
                    onAppSelected(item.name, item.packageName, item.apkFile)
                }
            }
        }

        dialog.show()
    }

    private class AppAdapter(
        private val list: List<InstalledAppItem>,
        private val onItemClick: (InstalledAppItem) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.VH>() {

        class VH(val binding: ItemAppToCloneBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemAppToCloneBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.binding.tvAppName.text = item.name
            holder.binding.tvPackageName.text = item.packageName
            holder.binding.ivAppIcon.setImageDrawable(item.icon)
            holder.itemView.setOnClickListener {
                onItemClick(item)
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
