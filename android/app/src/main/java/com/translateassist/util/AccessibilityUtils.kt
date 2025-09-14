package com.translateassist.util

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityUtils {
    /**
     * Returns true if the accessibility service is enabled in system settings, even if
     * our process was killed (i.e., instance == null). This prevents false "Disabled" UI states
     * after the user swipes the app from recents.
     */
    fun isServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        val expected = ComponentName(context, serviceClass).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
