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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast
import com.translateassist.R
import com.translateassist.translation.TranslationResult
import com.translateassist.translation.TranslationLinePair
import com.translateassist.translation.LineMode

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

        // Setup RecyclerView for line pairs
        val recycler = popupView?.findViewById<RecyclerView>(R.id.translation_list)
        recycler?.layoutManager = LinearLayoutManager(context)

        val basePairs: List<TranslationLinePair> = when {
            result.linePairs.isNotEmpty() -> result.linePairs
            result.originalText.isNotBlank() || result.translatedText.isNotBlank() -> listOf(
                TranslationLinePair(result.originalText, result.translatedText, null, LineMode.TRANSLATED)
            )
            else -> emptyList()
        }

        // Ensure chronological order: oldest message first, newest last.
        // Current symptom: newest appearing at top -> reverse only if we detect likely reversed order.
        // Simple approach: assume incoming is newest-first if last message was showing first; so reorder here.
        val pairs = if (basePairs.size > 1) basePairs.asReversed() else basePairs

        val adapter = TranslationPairAdapter(pairs) { copiedLine ->
            copyToClipboard(copiedLine)
        }
        recycler?.adapter = adapter

        // Set up buttons
        val copyButton = popupView?.findViewById<Button>(R.id.copy_button)
        val closeButton = popupView?.findViewById<ImageButton>(R.id.close_button)

        copyButton?.setOnClickListener {
            // Copy all translated lines in display order (chronological)
            val textToCopy = if (pairs.isNotEmpty()) {
                pairs.joinToString("\n") { it.chosenText() }
            } else result.translatedText
            copyToClipboard(textToCopy)
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