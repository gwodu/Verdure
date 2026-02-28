package com.verdure.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.verdure.R
import com.verdure.data.InstalledAppsManager
import com.verdure.data.UserContextManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App Prioritization UI
 *
 * Allows users to visually order their apps by priority using drag-and-drop.
 * First app = highest priority, last app = lowest priority.
 *
 * Architecture:
 * - Loads installed apps from InstalledAppsManager
 * - Displays in RecyclerView with drag-and-drop enabled
 * - Saves ordering to UserContext.priorityRules.customAppOrder
 * - Synced with chat-based prioritization (both update the same data)
 */
class AppPriorityActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppPriorityAdapter
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var instructionsText: TextView

    private lateinit var appsManager: InstalledAppsManager
    private lateinit var contextManager: UserContextManager

    private var hasUnsavedChanges = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_priority)

        // Initialize components
        recyclerView = findViewById(R.id.appPriorityRecyclerView)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
        instructionsText = findViewById(R.id.instructionsText)

        appsManager = InstalledAppsManager(this)
        contextManager = UserContextManager.getInstance(this)

        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AppPriorityAdapter(emptyList()) { position ->
            // Mark as changed when user drags
            hasUnsavedChanges = true
            updateSaveButtonState()
        }
        recyclerView.adapter = adapter

        // Enable drag-and-drop
        val itemTouchHelper = ItemTouchHelper(DragCallback(adapter))
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Load apps asynchronously
        loadApps()

        // Button handlers
        saveButton.setOnClickListener {
            saveAppOrder()
        }

        cancelButton.setOnClickListener {
            finish()
        }

        updateSaveButtonState()
    }

    /**
     * Load installed apps and apply current custom ordering
     */
    private fun loadApps() {
        lifecycleScope.launch {
            instructionsText.text = "Loading apps..."

            val installedApps = withContext(Dispatchers.IO) {
                appsManager.getInstalledApps()
            }

            // Load current custom order from context
            val context = contextManager.loadContext()
            val customOrder = context.priorityRules.customAppOrder

            // Sort apps by custom order (apps in order first, then alphabetical)
            val sortedApps = if (customOrder.isNotEmpty()) {
                val ordered = customOrder.mapNotNull { packageName ->
                    installedApps.find { it.packageName == packageName }
                }
                val remaining = installedApps.filterNot { app ->
                    customOrder.contains(app.packageName)
                }
                ordered + remaining
            } else {
                installedApps
            }

            adapter.updateApps(sortedApps)
            instructionsText.text = "Drag apps to reorder • First = highest priority"
            hasUnsavedChanges = false
            updateSaveButtonState()
        }
    }

    /**
     * Save the current app order to UserContext
     */
    private fun saveAppOrder() {
        lifecycleScope.launch {
            saveButton.isEnabled = false
            saveButton.text = "Saving..."

            val currentOrder = adapter.getAppOrder()
            contextManager.updateAppPriorityOrder(currentOrder)

            // Success feedback
            saveButton.text = "Saved!"
            hasUnsavedChanges = false

            // Return to main activity after brief delay
            kotlinx.coroutines.delay(500)
            finish()
        }
    }

    /**
     * Update save button state based on changes
     */
    private fun updateSaveButtonState() {
        saveButton.isEnabled = hasUnsavedChanges
        saveButton.text = if (hasUnsavedChanges) "Save Changes" else "No Changes"
    }

    /**
     * Callback for drag-and-drop functionality
     */
    private class DragCallback(
        private val adapter: AppPriorityAdapter
    ) : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        0
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPos = viewHolder.bindingAdapterPosition
            val toPos = target.bindingAdapterPosition
            adapter.moveItem(fromPos, toPos)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // No swipe actions
        }

        override fun isLongPressDragEnabled(): Boolean = true
    }
}
