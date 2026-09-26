package com.antilinkage.app.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.antilinkage.app.databinding.ItemClonedAppBinding
import com.antilinkage.app.model.ClonedAppInfo

class ClonedAppAdapter(
    private val instances: MutableList<ClonedAppInfo>,
    private val onLaunchClick: (ClonedAppInfo) -> Unit,
    private val onConfigClick: (ClonedAppInfo) -> Unit
) : RecyclerView.Adapter<ClonedAppAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemClonedAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemClonedAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = instances[position]
        val fp = item.fingerprint

        holder.binding.tvInstanceTitle.text = "${item.appName} (分身 #${item.userId})"
        holder.binding.tvFingerprintPreview.text = buildString {
            append("品牌/型号: ${fp.brand} ${fp.model}\n")
            append("Android ID: ${fp.androidId}\n")
            append("IMEI: ${fp.imei}\n")
            append("MAC: ${fp.macAddress}\n")
            append("虚拟坐标: ${fp.latitude}, ${fp.longitude}")
        }

        holder.binding.btnLaunch.setOnClickListener {
            onLaunchClick(item)
        }

        holder.binding.btnConfigFingerprint.setOnClickListener {
            onConfigClick(item)
        }
    }

    override fun getItemCount(): Int = instances.size
}
