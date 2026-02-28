package com.verdure.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.verdure.R
import com.verdure.data.InstalledAppsManager
import java.util.Collections

/**
 * RecyclerView adapter for drag-and-drop app prioritization
 *
 * Features:
 * - Displays app icon, name, and priority badge
 * - Supports drag-and-drop reordering
 * - Visual feedback for drag state
 */
class AppPriorityAdapter(
    private var apps: List<InstalledAppsManager.AppInfo>,
    private val onItemMoved: (position: Int) -> Unit
) : RecyclerView.Adapter<AppPriorityAdapter.AppViewHolder>() {

    private val mutableApps = apps.toMutableList()

    inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.appIcon)
        val appName: TextView = view.findViewById(R.id.appName)
        val priorityBadge: TextView = view.findViewById(R.id.priorityBadge)
        val dragHandle: ImageView = view.findViewById(R.id.dragHandle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_priority, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = mutableApps[position]
        
        holder.appIcon.setImageDrawable(app.icon)
        holder.appName.text = app.appName
        
        // Priority badge shows position (1 = highest)
        holder.priorityBadge.text = "${position + 1}"
        
        // Visual feedback: top 3 apps get accent color
        if (position < 3) {
            holder.priorityBadge.setBackgroundResource(R.drawable.badge_high_priority)
        } else {
            holder.priorityBadge.setBackgroundResource(R.drawable.badge_normal_priority)
        }
    }

    override fun getItemCount(): Int = mutableApps.size

    /**
     * Move item from one position to another (called by ItemTouchHelper)
     */
    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(mutableApps, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(mutableApps, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        onItemMoved(toPosition)
    }

    /**
     * Update the entire app list (used during initial load)
     */
    fun updateApps(newApps: List<InstalledAppsManager.AppInfo>) {
        mutableApps.clear()
        mutableApps.addAll(newApps)
        notifyDataSetChanged()
    }

    /**
     * Get current app order as list of package names
     */
    fun getAppOrder(): List<String> {
        return mutableApps.map { it.packageName }
    }
}
