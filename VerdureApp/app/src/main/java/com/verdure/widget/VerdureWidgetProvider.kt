package com.verdure.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import com.verdure.R
import com.verdure.data.NotificationSummaryStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Verdure Widget Provider
 * 
 * Displays LLM-summarized CRITICAL priority notifications on the home screen.
 * Updates automatically when new CRITICAL notifications arrive via NotificationSummarizationService.
 */
class VerdureWidgetProvider : AppWidgetProvider() {
    
    companion object {
        private const val TAG = "VerdureWidget"
        
        /**
         * Manually trigger widget update from external components
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, VerdureWidgetProvider::class.java)
            )
            
            if (widgetIds.isNotEmpty()) {
                val provider = VerdureWidgetProvider()
                provider.onUpdate(context, appWidgetManager, widgetIds)
                Log.d(TAG, "Manually updated ${widgetIds.size} widget(s)")
            }
        }
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widget(s)")
        
        // Update each widget instance
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onEnabled(context: Context) {
        Log.d(TAG, "First widget added")
    }
    
    override fun onDisabled(context: Context) {
        Log.d(TAG, "Last widget removed")
    }
    
    /**
     * Update a single widget instance
     */
    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val summaryStore = NotificationSummaryStore(context)
        val summary = summaryStore.getLatestSummary()
        
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        
        if (summary != null) {
            // Active state: Display summary
            views.setTextViewText(R.id.widget_summary, summary.text)
            
            // Format timestamp
            val timeStr = formatTimestamp(summary.timestamp)
            views.setTextViewText(R.id.widget_timestamp, "Updated $timeStr")
            
            Log.d(TAG, "Widget updated with summary (${summary.notificationIds.size} notifications)")
        } else {
            // Empty state: No critical notifications
            views.setTextViewText(R.id.widget_summary, "✓ All clear!\n\nNo urgent notifications")
            views.setTextViewText(R.id.widget_timestamp, "")
            
            Log.d(TAG, "Widget updated with empty state")
        }
        
        // Update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
    
    /**
     * Format timestamp to human-readable string
     */
    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60_000 -> "just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> {
                val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                dateFormat.format(Date(timestamp))
            }
            else -> {
                val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                dateFormat.format(Date(timestamp))
            }
        }
    }
}
