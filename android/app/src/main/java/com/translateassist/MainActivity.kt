package com.translateassist

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.translateassist.service.OverlayService
import com.translateassist.service.TranslateAccessibilityService
import com.translateassist.util.AccessibilityUtils
import com.translateassist.translation.TranslationEngine
import com.translateassist.ui.TranslationPopup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var translationEngine: TranslationEngine
    private lateinit var translationPopup: TranslationPopup
    
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
        translationEngine = TranslationEngine(this)
        translationPopup = TranslationPopup(this)
        
        // Set up overlay button click handler
        OverlayService.onOverlayClicked = {
            handleOverlayClick()
        }
        
        // Set up accessibility service text extraction handler
        TranslateAccessibilityService.onTextExtracted = { text ->
            translateAndShowResult(text)
        }
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

    private fun handleOverlayClick() {
        android.util.Log.d("MainActivity", "Overlay button clicked!")
        Toast.makeText(this, "Translate button clicked!", Toast.LENGTH_SHORT).show()
        
        val systemEnabled = com.translateassist.util.AccessibilityUtils.isServiceEnabled(this, TranslateAccessibilityService::class.java)
        val live = TranslateAccessibilityService.instance
        if (live != null) {
            android.util.Log.d("MainActivity", "Accessibility service active, extracting...")
            live.extractTextFromWhatsApp()
            return
        }
        if (!systemEnabled) {
            Toast.makeText(this, "Enable accessibility service first", Toast.LENGTH_SHORT).show()
            android.util.Log.w("MainActivity", "Accessibility service disabled in system settings")
            return
        }
        // System toggle ON but service instance not yet bound (process restart race). Queue action.
        Toast.makeText(this, "Starting accessibility service...", Toast.LENGTH_SHORT).show()
        android.util.Log.d("MainActivity", "Queueing extraction until service connects")
        TranslateAccessibilityService.runWhenReady {
            runOnUiThread {
                TranslateAccessibilityService.instance?.extractTextFromWhatsApp()
            }
        }
        // Fallback timeout message if not ready in ~3s
        statusText.postDelayed({
            if (TranslateAccessibilityService.instance == null) {
                android.util.Log.w("MainActivity", "Service still not connected after wait")
                Toast.makeText(this, "Still waiting for service. Re-open Accessibility settings if it doesn't start.", Toast.LENGTH_LONG).show()
            }
        }, 3000)
    }

    private fun translateAndShowResult(text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, "No text found to translate", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Start streaming popup
                translationPopup.startStreaming()
                val full = translationEngine.translateTextStreaming(text) { pair ->
                    // Ensure on main thread for UI update
                    runOnUiThread { translationPopup.appendStreamingPair(pair) }
                }
                // Finalize (hide loader if still visible)
                translationPopup.finalizeStreaming()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Translation failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
            startService(intent)
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
        val appName = getString(R.string.app_name)
        statusText.text = """
            Overlay Service: $overlayStatus
            Accessibility Service: $accessibilityStatus  
            Overlay Permission: $overlayPermission

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
        if (hasOverlayPermission() && OverlayService.instance == null && accessibilitySystemEnabled) {
            // Lightweight auto start; user already approved permissions previously
            startService(Intent(this, OverlayService::class.java))
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
        translationEngine.cleanup()
        translationPopup.hidePopup()
    }

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
    }
}