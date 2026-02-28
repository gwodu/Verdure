package com.verdure.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/**
 * Manages discovery and tracking of installed apps on the device
 *
 * Used for:
 * - Populating app prioritization UI
 * - Validating priority changes (can't prioritize non-existent apps)
 * - Filtering out system apps and non-notifying apps
 */
class InstalledAppsManager(private val context: Context) {

    data class AppInfo(
        val packageName: String,
        val appName: String,
        val icon: Drawable
    )

    /**
     * Get all user-installed apps that can post notifications
     * Filters out system apps and non-user-facing apps
     */
    fun getInstalledApps(): List<AppInfo> {
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        return installedApps
            .filter { shouldIncludeApp(it) }
            .mapNotNull { appInfo ->
                try {
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = packageManager.getApplicationIcon(appInfo)
                    AppInfo(appInfo.packageName, appName, icon)
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.appName.lowercase() }
    }

    /**
     * Get user-friendly app name from package name
     */
    fun getAppName(packageName: String): String? {
        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if an app is installed
     */
    fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Find app by name (case-insensitive fuzzy match)
     * Returns package name if found
     */
    fun findAppByName(appName: String): String? {
        val installedApps = getInstalledApps()
        val lowerQuery = appName.lowercase()

        // First try exact match
        installedApps.find { it.appName.lowercase() == lowerQuery }?.let {
            return it.packageName
        }

        // Then try contains match
        installedApps.find { it.appName.lowercase().contains(lowerQuery) }?.let {
            return it.packageName
        }

        return null
    }

    /**
     * Decide if an app should be included in the prioritization UI
     *
     * Filters:
     * - System apps (unless they're useful like Phone, Messages, Calendar)
     * - Non-launchable apps
     * - Our own app (Verdure)
     */
    private fun shouldIncludeApp(appInfo: ApplicationInfo): Boolean {
        val packageManager = context.packageManager

        // Exclude Verdure itself
        if (appInfo.packageName == context.packageName) {
            return false
        }

        // Include if it has a launcher intent (user-facing app)
        val hasLauncher = packageManager.getLaunchIntentForPackage(appInfo.packageName) != null

        // System apps: include only if they have a launcher (excludes background services)
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        if (isSystemApp) {
            return hasLauncher && isUsefulSystemApp(appInfo.packageName)
        }

        // Include all user-installed apps
        return hasLauncher
    }

    /**
     * Whitelist of system apps that should be included
     * (Phone, Messages, Calendar, etc.)
     */
    private fun isUsefulSystemApp(packageName: String): Boolean {
        val usefulSystemApps = setOf(
            "com.google.android.dialer",
            "com.google.android.apps.messaging",
            "com.android.messaging",
            "com.google.android.calendar",
            "com.android.calendar",
            "com.google.android.gm", // Gmail
            "com.android.chrome",
            "com.google.android.apps.maps",
            "com.android.vending" // Play Store
        )

        return packageName in usefulSystemApps || 
               packageName.contains("dialer", ignoreCase = true) ||
               packageName.contains("messaging", ignoreCase = true) ||
               packageName.contains("calendar", ignoreCase = true)
    }
}
