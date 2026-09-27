package com.antilinkage.app.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.antilinkage.app.databinding.ItemClonedAppBinding
import com.antilinkage.app.databinding.ItemClonedAppGridBinding
import com.antilinkage.app.model.ClonedAppInfo
import com.antilinkage.app.util.AppIconHelper

class ClonedAppAdapter(
    private val instances: MutableList<ClonedAppInfo>,
    var isGridMode: Boolean = true,
    private val onLaunchClick: (ClonedAppInfo) -> Unit,
    private val onConfigClick: (ClonedAppInfo) -> Unit,
    private val onOptionsClick: ((ClonedAppInfo, View) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_GRID = 1
        const val TYPE_LIST = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) TYPE_GRID else TYPE_LIST
    }

    class GridViewHolder(val binding: ItemClonedAppGridBinding) : RecyclerView.ViewHolder(binding.root)
    class ListViewHolder(val binding: ItemClonedAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GRID) {
            GridViewHolder(ItemClonedAppGridBinding.inflate(inflater, parent, false))
        } else {
            ListViewHolder(ItemClonedAppBinding.inflate(inflater, parent, false))
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = instances[position]
        val context = holder.itemView.context
        val icon = AppIconHelper.getAppIcon(context, item)

        if (holder is GridViewHolder) {
            holder.binding.ivAppIcon.setImageDrawable(icon)
            holder.binding.tvCloneBadge.text = "#${item.userId}"
            holder.binding.tvAppName.text = item.appName

            holder.itemView.setOnClickListener {
                onLaunchClick(item)
            }
            holder.itemView.setOnLongClickListener { view ->
                onOptionsClick?.invoke(item, view) ?: onConfigClick(item)
                true
            }
        } else if (holder is ListViewHolder) {
            holder.binding.ivAppIcon.setImageDrawable(icon)
            holder.binding.tvCloneBadge.text = "#${item.userId}"
            holder.binding.tvInstanceTitle.text = "${item.appName} (分身 #${item.userId})"
            holder.binding.tvSubtitle.text = "${item.fingerprint.brand} ${item.fingerprint.model} · 独立指纹"

            holder.binding.btnLaunch.setOnClickListener {
                onLaunchClick(item)
            }
            holder.binding.btnConfigFingerprint.setOnClickListener {
                onConfigClick(item)
            }
            holder.itemView.setOnClickListener {
                onLaunchClick(item)
            }
            holder.itemView.setOnLongClickListener { view ->
                onOptionsClick?.invoke(item, view) ?: onConfigClick(item)
                true
            }
        }
    }

    override fun getItemCount(): Int = instances.size
}
