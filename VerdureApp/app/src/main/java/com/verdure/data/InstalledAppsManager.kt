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
     * Simple approach: Include ANY app with a launcher icon
     * This catches all user-facing apps (both system and user-installed)
     *
     * Filters out:
     * - Background services (no launcher)
     * - Verdure itself
     */
    private fun shouldIncludeApp(appInfo: ApplicationInfo): Boolean {
        val packageManager = context.packageManager

        // Exclude Verdure itself
        if (appInfo.packageName == context.packageName) {
            return false
        }

        // Include ANY app with a launcher intent (system or user-installed)
        // This is the key: we trust that apps with launcher icons are user-facing
        return packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
    }
}
