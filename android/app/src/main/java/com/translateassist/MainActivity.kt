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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            overlayButton.text = "Stop Overlay"
        } else {
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
        val appName = getString(R.string.app_name)
        statusText.text = """
            Overlay Service: $overlayStatus
            Accessibility Service: $accessibilityStatus  
            Overlay Permission: $overlayPermission
            Notification Permission: $notifPermission

            Setup Steps:
            1. Tap 'Start Overlay' to grant overlay permission
            2. Tap 'Enable Accessibility Service' and turn on $appName
            3. Start the overlay service
            4. Open WhatsApp and tap the floating dot to translate

            Note: These are special Android permissions that require manual approval in system settings.
        """.trimIndent()

        overlayButton.text = if (OverlayService.instance != null) "Stop Overlay" else "Start Overlay"
        overlayButton.isEnabled = hasOverlayPermission()

        // Auto-start overlay if user swiped app away and reopened, while permissions intact
        if (hasOverlayPermission() && OverlayService.instance == null && accessibilitySystemEnabled && ensureNotificationPermissionIfNeeded()) {
            // Lightweight auto start; user already approved permissions previously
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
                    startActivity(intent)
                    return true
                }
            } catch (_: Exception) { /* try next */ }
        }
        return false
    }

    /**
     * Shows a one-time guidance dialog (per install) that walks the user through the OEM settings
     * needed to stop the system from killing the service. Re-shown only while the app is still not
     * exempt from battery optimisation.
     */
    private fun maybeShowKeepAliveGuidance() {
        if (isIgnoringBatteryOptimizations()) return
        val prefs = getSharedPreferences("translateassist", Context.MODE_PRIVATE)
        if (prefs.getBoolean("keepalive_guidance_shown", false)) {
            // Still ask for the battery exemption silently even if we've shown the dialog once.
            requestBatteryOptimizationExemption()
            return
        }
        prefs.edit().putBoolean("keepalive_guidance_shown", true).apply()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Keep TranslateAssist running")
            .setMessage(
                """
                To avoid re-granting the Accessibility permission every time you clear recent apps:

                1. Allow background activity (battery) — tap "Allow background".
                2. Enable Autostart for TranslateAssist.
                3. In recent apps, lock TranslateAssist so it isn't swiped away.

                You only need to do this once.
                """.trimIndent()
            )
            .setPositiveButton("Allow background") { _, _ ->
                requestBatteryOptimizationExemption()
                if (!openAutoStartSettings()) {
                    Toast.makeText(this, "Open Settings > Apps > TranslateAssist and enable Autostart", Toast.LENGTH_LONG).show()
                }
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