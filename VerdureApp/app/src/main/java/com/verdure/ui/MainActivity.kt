package com.verdure.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.verdure.R
import com.verdure.services.VerdureNotificationListener
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var requestPermissionButton: Button
    private lateinit var notificationCountText: TextView
    private lateinit var notificationListText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        requestPermissionButton = findViewById(R.id.requestPermissionButton)
        notificationCountText = findViewById(R.id.notificationCountText)
        notificationListText = findViewById(R.id.notificationListText)

        requestPermissionButton.setOnClickListener {
            requestNotificationListenerPermission()
        }

        checkPermissionAndSetupObserver()
    }

    override fun onResume() {
        super.onResume()
        // Re-check permission when returning to the app
        checkPermissionAndSetupObserver()
    }

    /**
     * Check if notification listener permission is granted.
     */
    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        val componentName = ComponentName(this, VerdureNotificationListener::class.java)
        return enabledListeners?.contains(componentName.flattenToString()) == true
    }

    /**
     * Open settings to allow user to grant notification listener permission.
     */
    private fun requestNotificationListenerPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    /**
     * Check permission and set up notification observer if granted.
     */
    private fun checkPermissionAndSetupObserver() {
        if (isNotificationListenerEnabled()) {
            statusText.text = "✅ Notification access enabled"
            requestPermissionButton.isEnabled = false
            requestPermissionButton.text = "Notification Access Granted"

            // Observe notifications from the service
            observeNotifications()
        } else {
            statusText.text = "⚠️ Notification access required"
            requestPermissionButton.isEnabled = true
            requestPermissionButton.text = "Enable Notification Access"
        }
    }

    /**
     * Observe the notifications StateFlow and update UI.
     */
    private fun observeNotifications() {
        lifecycleScope.launch {
            VerdureNotificationListener.notifications.collect { notifications ->
                if (notifications.isEmpty()) {
                    notificationCountText.text = "No notifications captured yet"
                    notificationListText.text = "Waiting for notifications...\n\nOnce you receive notifications on your phone, they will appear here."
                } else {
                    notificationCountText.text = "Captured ${notifications.size} notification(s)"

                    // Format notifications for display
                    val displayText = notifications.joinToString("\n\n---\n\n") { notif ->
                        buildString {
                            append("📱 ${notif.appName}\n")
                            append("⏰ ${notif.getFormattedTime()}\n")
                            if (!notif.title.isNullOrBlank()) {
                                append("📌 ${notif.title}\n")
                            }
                            if (!notif.text.isNullOrBlank()) {
                                append("💬 ${notif.text}\n")
                            }
                            append("🔖 Package: ${notif.packageName}\n")
                            append("⚡ Priority: ${notif.priority}")
                        }
                    }

                    notificationListText.text = displayText
                }
            }
        }
    }
}
