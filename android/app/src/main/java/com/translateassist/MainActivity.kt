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
        val accessibilityService = TranslateAccessibilityService.instance
        if (accessibilityService == null) {
            Toast.makeText(this, "Please enable accessibility service first", Toast.LENGTH_SHORT).show()
            return
        }

        // First try to get selected text
        val selectedText = accessibilityService.getSelectedText()
        if (!selectedText.isNullOrBlank()) {
            translateAndShowResult(selectedText)
        } else {
            // If no text is selected, extract recent messages
            accessibilityService.extractTextFromWhatsApp()
        }
    }

    private fun translateAndShowResult(text: String) {
        if (text.isBlank()) {
            Toast.makeText(this, "No text found to translate", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = translationEngine.translateText(text)
                translationPopup.showTranslation(result)
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
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
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
        Toast.makeText(this, "Please enable 'TranslateAssist' accessibility service", Toast.LENGTH_LONG).show()
    }

    private fun updateStatus() {
        val overlayStatus = if (OverlayService.instance != null) "Running" else "Stopped"
        val accessibilityStatus = if (TranslateAccessibilityService.instance != null) "Enabled" else "Disabled"
        val overlayPermission = if (hasOverlayPermission()) "Granted" else "Not Granted"
        
        statusText.text = """
            Overlay Service: $overlayStatus
            Accessibility Service: $accessibilityStatus  
            Overlay Permission: $overlayPermission
            
            Instructions:
            1. Grant overlay permission
            2. Enable accessibility service
            3. Start overlay service
            4. Open WhatsApp and tap the floating button to translate
        """.trimIndent()

        overlayButton.text = if (OverlayService.instance != null) "Stop Overlay" else "Start Overlay"
        overlayButton.isEnabled = hasOverlayPermission()
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

    override fun onDestroy() {
        super.onDestroy()
        translationEngine.cleanup()
        translationPopup.hidePopup()
    }

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
    }
}