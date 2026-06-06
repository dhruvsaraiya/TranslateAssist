package com.translateassist.util

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.provider.Settings

object AccessibilityUtils {
    /**
     * Returns true if the accessibility service is enabled in system settings, even if
     * our process was killed (i.e., instance == null). This prevents false "Disabled" UI states
     * after the user swipes the app from recents.
     */
    fun isServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        // 1) Fast path: ask AccessibilityManager (reflects what the system believes is enabled)
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val targetPkg = context.packageName
            val targetClass = serviceClass.name
            if (enabledServices != null) {
                val found = enabledServices.any { info ->
                    val si = info.resolveInfo?.serviceInfo
                    si?.packageName == targetPkg && si?.name == targetClass
                }
                if (found) return true
            }
        } catch (_: Exception) {
            // Ignore and fall back to Settings.Secure parsing.
        }

        // 2) Fallback: parse Settings.Secure string. Some OEMs store flattened components in different forms
        // (e.g., com.pkg/.MyService vs com.pkg/com.pkg.MyService), so normalize before comparing.
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val target = ComponentName(context, serviceClass)
        val targetPkg = target.packageName
        val targetClass = target.className

        return enabled.split(':').any { raw ->
            val cn = ComponentName.unflattenFromString(raw) ?: return@any false
            val pkg = cn.packageName
            val cls = cn.className.let { if (it.startsWith(".")) pkg + it else it }
            pkg.equals(targetPkg, ignoreCase = true) && cls.equals(targetClass, ignoreCase = true)
        }
    }
}
