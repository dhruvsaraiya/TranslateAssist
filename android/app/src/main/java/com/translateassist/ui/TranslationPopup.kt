package com.translateassist.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.translateassist.R
import com.translateassist.translation.TranslationResult

class TranslationPopup(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var popupView: View? = null
    private var isShowing = false

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    fun showTranslation(result: TranslationResult) {
        if (isShowing) {
            hidePopup()
        }

        createPopupView(result)
        showPopup()
    }

    private fun createPopupView(result: TranslationResult) {
        popupView = LayoutInflater.from(context).inflate(R.layout.translation_popup, null)

        // Set up text views
        val originalTextView = popupView?.findViewById<TextView>(R.id.original_text)
        val translatedTextView = popupView?.findViewById<TextView>(R.id.translated_text)
        val languageInfoView = popupView?.findViewById<TextView>(R.id.language_info)

        originalTextView?.text = result.originalText
        translatedTextView?.text = result.translatedText
        languageInfoView?.text = "${result.detectedLanguage} → ${result.translationType}"

        // Set up buttons
        val copyButton = popupView?.findViewById<Button>(R.id.copy_button)
        val closeButton = popupView?.findViewById<ImageButton>(R.id.close_button)

        copyButton?.setOnClickListener {
            copyToClipboard(result.translatedText)
        }

        closeButton?.setOnClickListener {
            hidePopup()
        }

        // Close popup when clicking outside
        popupView?.setOnClickListener {
            hidePopup()
        }

        // Prevent clicks on the content area from closing the popup
        popupView?.findViewById<View>(R.id.popup_content)?.setOnClickListener { 
            // Do nothing - prevent click from propagating
        }
    }

    private fun showPopup() {
        if (popupView == null) return

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager?.addView(popupView, params)
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hidePopup() {
        if (isShowing && popupView != null) {
            try {
                windowManager?.removeView(popupView)
                isShowing = false
                popupView = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("Translated Text", text)
        clipboardManager.setPrimaryClip(clipData)
        
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun isVisible(): Boolean = isShowing
}