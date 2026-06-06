package com.translateassist

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import com.translateassist.service.OverlayService
import com.translateassist.service.OverlayClickHandler
import com.translateassist.service.TranslateAccessibilityService
import com.translateassist.util.AccessibilityUtils
import com.translateassist.translation.TranslationController

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var overlayButton: Button
    private lateinit var accessibilityButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        initializeComponents()
        setupClickListeners()
        updateStatus()
        
        // Check and request permissions on first launch
        checkPermissionsOnStart()
    }

    private fun initializeViews() {
        statusText = findViewById(R.id.status_text)
        overlayButton = findViewById(R.id.overlay_button)
        accessibilityButton = findViewById(R.id.accessibility_button)
    }

    private fun initializeComponents() {
        // The translation engine + popup now live in a process-wide controller so they survive the
        // Activity being destroyed (e.g. when the app is swiped from recents). This is what lets the
        // floating overlay keep translating without reopening the app.
        TranslationController.init(this)

        // Route the overlay tap through the shared handler so it behaves identically whether or not
        // this Activity is alive.
        OverlayService.onOverlayClicked = { OverlayClickHandler.onClick(this) }

        // Let the accessibility service deliver extracted text straight to the controller.
        TranslateAccessibilityService.onTextExtracted = null
    }

    private fun setupClickListeners() {
        overlayButton.setOnClickListener {
            if (hasOverlayPermission()) {
                toggleOverlayService()
            } else {
                requestOverlayPermission()
            }
        }

        accessibilityButton.setOnClickListener {
            openAccessibilitySettings()
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
                Toast.makeText(this, "Please allow 'Display over other apps' permission", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun toggleOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (OverlayService.instance == null) {
            if (!ensureNotificationPermissionIfNeeded()) {
                Toast.makeText(this, "Allow notifications so the overlay can stay running", Toast.LENGTH_LONG).show()
                updateStatus()
                return
            }
            OverlayService.setEnabled(this, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            overlayButton.text = "Stop Overlay"
        } else {
            // Record the explicit user intent BEFORE stopping so no auto-start path revives it.
            OverlayService.setEnabled(this, false)
            stopService(intent)
            overlayButton.text = "Start Overlay"
        }
        updateStatus()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Please enable '${getString(R.string.app_name)}' accessibility service", Toast.LENGTH_LONG).show()
    }

    private fun updateStatus() {
        val overlayStatus = if (OverlayService.instance != null) "Running" else "Stopped"
        val accessibilitySystemEnabled = AccessibilityUtils.isServiceEnabled(this, TranslateAccessibilityService::class.java)
        val accessibilityStatus = if (accessibilitySystemEnabled) {
            if (TranslateAccessibilityService.instance != null) "Active" else "Enabled (waiting)"
        } else "Disabled"
        val overlayPermission = if (hasOverlayPermission()) "Granted" else "Not Granted"
        val notifPermission = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> "Not required"
            hasNotificationPermission() -> "Granted"
            else -> "Not Granted"
        }
        val batteryOptimization = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> "Not required"
            isIgnoringBatteryOptimizations() -> "No restrictions"
            else -> "Needs approval"
        }
        val autoStartStatus = if (findAutoStartSettingsIntent() != null) {
            "Manual setup needed"
        } else {
            "Check phone settings"
        }
        val appName = getString(R.string.app_name)
        statusText.text = """
            Overlay Service: $overlayStatus
            Accessibility Service: $accessibilityStatus  
            Overlay Permission: $overlayPermission
            Notification Permission: $notifPermission
            Battery Optimization: $batteryOptimization
            Autostart / Auto-launch: $autoStartStatus

            Setup Steps:
            1. Allow notifications if asked
            2. Tap 'Grant Overlay Permission' if overlay permission is missing
            3. Tap 'Enable Accessibility Service' and turn on $appName
            4. Enable Autostart / Auto-launch for $appName
            5. Set Battery saver to No restrictions
            6. Tap 'Start Overlay' to show the floating button
            7. Open WhatsApp and tap the floating dot to translate

            Note: Autostart is an OEM setting, so Android does not reliably report whether it is already enabled.
        """.trimIndent()

        overlayButton.text = when {
            OverlayService.instance != null -> "Stop Overlay"
            !hasOverlayPermission() -> "Grant Overlay Permission"
            else -> "Start Overlay"
        }
        overlayButton.isEnabled = true

        // Auto-start overlay only if the user previously had it on (and didn't explicitly stop it),
        // e.g. after swiping the app away and reopening it. OverlayService.isEnabled() is the single
        // source of truth so "Stop Overlay" actually sticks.
        if (OverlayService.isEnabled(this) && hasOverlayPermission() && OverlayService.instance == null &&
            accessibilitySystemEnabled && ensureNotificationPermissionIfNeeded()) {
            val serviceIntent = Intent(this, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            overlayButton.text = "Stop Overlay"
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            updateStatus()
        }
    }

    private fun checkPermissionsOnStart() {
        // Check overlay permission
        if (!hasOverlayPermission()) {
            showPermissionExplanationDialog()
        }

        // Request notifications permission on Android 13+ so foreground service notification can be posted.
        ensureNotificationPermissionIfNeeded()

        // The single biggest cause of "the overlay comes but nothing works after clearing recents"
        // is the OS killing our process and never rebinding the accessibility service. Asking the
        // user to exempt us from battery optimisation and to enable Autostart (MIUI/HyperOS, etc.)
        // keeps the process alive so the permission only ever needs to be granted once.
        maybeShowKeepAliveGuidance()
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (isIgnoringBatteryOptimizations()) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fall back to the general battery optimisation list.
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Please allow background activity for TranslateAssist in Settings", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Opens the OEM "Autostart" / "Auto-launch" manager when present (MIUI/HyperOS, Huawei, Oppo,
     * Vivo, etc.). These OEMs kill the process on a recents-swipe and only relaunch background
     * components if the app is on the autostart allow-list.
     */
    private fun openAutoStartSettings(): Boolean {
        val intent = findAutoStartSettingsIntent() ?: return false
        return try {
            startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun findAutoStartSettingsIntent(): Intent? {
        val candidates = listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
        )
        for (cn in candidates) {
            try {
                val intent = Intent().setComponent(cn)
                if (packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                    return intent
                }
            } catch (_: Exception) { /* try next */ }
        }
        return null
    }

    /**
     * Shows a one-time guidance dialog (per install) for the OEM settings that let the system
     * rebind our accessibility service after the process is killed — so the user never has to
     * toggle the Accessibility permission off/on again.
     *
     * On MIUI/HyperOS, Huawei, Oppo, Vivo, etc. the key enabler is "Autostart" / "Auto-launch":
     * without it the OS blocks our package from being relaunched in the background, which is why
     * the permission appears enabled yet the service never rebinds. This is independent of battery
     * optimisation, so we surface it regardless of the battery-exemption state.
     */
    private fun maybeShowKeepAliveGuidance() {
        val prefs = getSharedPreferences("translateassist", Context.MODE_PRIVATE)
        if (prefs.getBoolean("keepalive_guidance_shown", false)) return
        prefs.edit().putBoolean("keepalive_guidance_shown", true).apply()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("One-time setup")
            .setMessage(
                """
                So you never have to re-toggle the Accessibility permission after clearing recent apps, grant these once:

                1. Enable Autostart / Auto-launch for TranslateAssist (this is the important one — it lets the service start again on its own).
                2. Allow background / no battery restrictions.

                It's fine if the floating dot disappears when you clear recents — just reopen the app and tap "Start Overlay" and it will work.
                """.trimIndent()
            )
            .setPositiveButton("Open Autostart") { _, _ ->
                if (!openAutoStartSettings()) {
                    Toast.makeText(this, "Open Settings > Apps > TranslateAssist and enable Autostart", Toast.LENGTH_LONG).show()
                }
                requestBatteryOptimizationExemption()
            }
            .setNegativeButton("Later") { d, _ -> d.dismiss() }
            .show()
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Returns true if notifications are either not required or already granted.
     * If not granted, triggers a one-time permission prompt and returns false.
     */
    private fun ensureNotificationPermissionIfNeeded(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        if (hasNotificationPermission()) return true
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATIONS_PERMISSION
        )
        return false
    }
    
    private fun showPermissionExplanationDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Permissions Required")
        builder.setMessage("""
                ${getString(R.string.app_name)} needs two permissions to work:
            
            1. Display over other apps - To show the floating translate button
            2. Accessibility Service - To read text from WhatsApp
            
            Would you like to grant these permissions now?
        """.trimIndent())
        
        builder.setPositiveButton("Grant Permissions") { _, _ ->
            requestOverlayPermission()
        }
        
        builder.setNegativeButton("Later") { dialog, _ ->
            dialog.dismiss()
        }
        
        builder.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Intentionally do NOT tear down the translation engine or popup here: they are owned by the
        // process-wide TranslationController so the floating overlay keeps working after this
        // Activity is destroyed (e.g. the app is swiped away from recents).
    }

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
        private const val REQUEST_NOTIFICATIONS_PERMISSION = 1002
    }
}