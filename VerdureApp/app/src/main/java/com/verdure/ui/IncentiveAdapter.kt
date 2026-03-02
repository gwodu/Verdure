package com.verdure.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.verdure.R
import com.verdure.data.Incentive

class IncentiveAdapter(
    private var incentives: List<Incentive>,
    private val matchCounts: Map<String, Int>,
    private val onItemClick: (Incentive) -> Unit,
    private val onToggleClick: (Incentive) -> Unit,
    private val onDeleteClick: (Incentive) -> Unit
) : RecyclerView.Adapter<IncentiveAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.incentiveName)
        val summary: TextView = view.findViewById(R.id.incentiveSummary)
        val status: TextView = view.findViewById(R.id.incentiveStatus)
        val matchCount: TextView = view.findViewById(R.id.matchCount)
        val toggleButton: TextView = view.findViewById(R.id.toggleButton)
        val deleteButton: TextView = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_incentive, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val incentive = incentives[position]
        val count = matchCounts[incentive.id] ?: 0

        holder.name.text = incentive.name
        holder.summary.text = incentive.aiSummary
        holder.matchCount.text = "$count notification${if (count != 1) "s" else ""} tracked"

        // Status badge
        if (incentive.isActive) {
            holder.status.text = "Active"
            holder.status.setTextColor(holder.itemView.context.getColor(R.color.accent_primary))
            holder.toggleButton.text = "Pause"
        } else {
            holder.status.text = "Paused"
            holder.status.setTextColor(holder.itemView.context.getColor(R.color.text_tertiary))
            holder.toggleButton.text = "Resume"
        }

        // Click handlers
        holder.itemView.setOnClickListener { onItemClick(incentive) }
        holder.toggleButton.setOnClickListener { 
            onToggleClick(incentive)
        }
        holder.deleteButton.setOnClickListener { 
            onDeleteClick(incentive)
        }
    }

    override fun getItemCount() = incentives.size

    fun updateData(newIncentives: List<Incentive>, newMatchCounts: Map<String, Int>) {
        incentives = newIncentives
        notifyDataSetChanged()
    }
}
