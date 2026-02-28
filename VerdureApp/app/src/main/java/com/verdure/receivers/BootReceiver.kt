package com.verdure.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver that starts NotificationSummarizationService on device boot.
 * Ensures widget remains functional after device restart without requiring app launch.
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "VerdureBootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed - starting NotificationSummarizationService")
            
            try {
                val serviceIntent = Intent(context, com.verdure.services.NotificationSummarizationService::class.java)
                context.startService(serviceIntent)
                Log.d(TAG, "NotificationSummarizationService started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start NotificationSummarizationService on boot", e)
            }
        }
    }
}
