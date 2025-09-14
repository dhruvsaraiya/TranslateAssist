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

    /**
     * Streaming version: show popup immediately with loader, then feed line pairs one-by-one.
     * Call finalizeStreaming() when done to hide loader (if not already) and enable copy button.
     */
    fun startStreaming(onCopyAll: (() -> String)? = null) {
        if (isShowing) hidePopup()
        popupView = LayoutInflater.from(context).inflate(R.layout.translation_popup, null)
        val recycler = popupView?.findViewById<RecyclerView>(R.id.translation_list)
        recycler?.layoutManager = LinearLayoutManager(context)
        val adapter = TranslationPairAdapter(emptyList()) { copiedLine -> copyToClipboard(copiedLine) }
        recycler?.adapter = adapter
        popupView?.findViewById<View>(R.id.loading_overlay)?.visibility = View.VISIBLE
        popupView?.findViewById<Button>(R.id.copy_button)?.isEnabled = false
        popupView?.findViewById<Button>(R.id.copy_button)?.setOnClickListener {
            val all = onCopyAll?.invoke()?.ifBlank { "" } ?: adapter.getAllPairs().joinToString("\n") { it.chosenText() }
            if (all.isNotBlank()) copyToClipboard(all)
        }
        popupView?.findViewById<ImageButton>(R.id.close_button)?.setOnClickListener { hidePopup() }
        popupView?.setOnClickListener { hidePopup() }
        popupView?.findViewById<View>(R.id.popup_content)?.setOnClickListener { }
        showPopup()
    }

    fun appendStreamingPair(pair: TranslationLinePair) {
        val recycler = popupView?.findViewById<RecyclerView>(R.id.translation_list) ?: return
        val adapter = recycler.adapter as? TranslationPairAdapter ?: return
        val loading = popupView?.findViewById<View>(R.id.loading_overlay)
        val wasEmpty = adapter.itemCount == 0
        adapter.addPair(pair)
        if (wasEmpty) {
            loading?.visibility = View.GONE
        }
        recycler.scrollToPosition(adapter.itemCount - 1)
    }

    fun finalizeStreaming() {
        popupView?.findViewById<View>(R.id.loading_overlay)?.visibility = View.GONE
        popupView?.findViewById<Button>(R.id.copy_button)?.isEnabled = true
    }

    private fun createPopupView(result: TranslationResult) {
        popupView = LayoutInflater.from(context).inflate(R.layout.translation_popup, null)

        // Setup RecyclerView for line pairs
        val recycler = popupView?.findViewById<RecyclerView>(R.id.translation_list)
        recycler?.layoutManager = LinearLayoutManager(context)

        val loadingOverlay = popupView?.findViewById<View>(R.id.loading_overlay)

        val basePairs: List<TranslationLinePair> = when {
            result.linePairs.isNotEmpty() -> result.linePairs
            result.originalText.isNotBlank() || result.translatedText.isNotBlank() -> listOf(
                TranslationLinePair(result.originalText, result.translatedText, null, LineMode.TRANSLATED)
            )
            else -> emptyList()
        }
        val orderedPairs = if (basePairs.size > 1) basePairs.asReversed() else basePairs

    val adapter = TranslationPairAdapter(emptyList()) { copiedLine -> copyToClipboard(copiedLine) }
        recycler?.adapter = adapter

        // Show loader while starting incremental rendering
        loadingOverlay?.visibility = View.VISIBLE

        // Incrementally add items (simple posting loop). Could be improved with coroutines if needed.
        if (orderedPairs.isEmpty()) {
            loadingOverlay?.visibility = View.GONE
        } else {
            recycler?.post {
                orderedPairs.forEachIndexed { index, pair ->
                    recycler.postDelayed({
                        if (index == 0) loadingOverlay?.visibility = View.GONE
                        adapter.addPair(pair)
                        recycler.scrollToPosition(adapter.itemCount - 1)
                    }, (index * 40L)) // 40ms stagger for perception; adjust as desired
                }
            }
        }

        // Set up buttons
        val copyButton = popupView?.findViewById<Button>(R.id.copy_button)
        val closeButton = popupView?.findViewById<ImageButton>(R.id.close_button)

        copyButton?.setOnClickListener {
            // Copy all (current) translated lines in display order
            val textToCopy = StringBuilder().apply {
                // Access adapter items reflectively (we didn't expose list; could add getter)
            }.toString().ifBlank { result.translatedText }
            // Simpler: rebuild from result source for now
            val allFromResult = orderedPairs.joinToString("\n") { it.chosenText() }
            copyToClipboard(if (allFromResult.isNotBlank()) allFromResult else result.translatedText)
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