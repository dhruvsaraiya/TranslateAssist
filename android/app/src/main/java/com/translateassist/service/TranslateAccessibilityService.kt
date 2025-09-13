package com.translateassist.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class TranslateAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TranslateAccessibility"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        var instance: TranslateAccessibilityService? = null
        var onTextExtracted: ((String) -> Unit)? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We only process events when explicitly requested via extractTextFromWhatsApp()
        // This prevents unnecessary processing and improves performance
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * Extract text from WhatsApp when overlay button is tapped
     */
    fun extractTextFromWhatsApp() {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.w(TAG, "Root node is null")
            return
        }

        val packageName = rootNode.packageName?.toString()
        if (packageName != WHATSAPP_PACKAGE) {
            Log.w(TAG, "Not in WhatsApp app: $packageName")
            return
        }

        val extractedTexts = mutableListOf<String>()
        extractTextFromNode(rootNode, extractedTexts)

        if (extractedTexts.isNotEmpty()) {
            // Get the most recent messages (usually at the end of the list)
            val recentMessages = extractedTexts.takeLast(5).joinToString("\n")
            onTextExtracted?.invoke(recentMessages)
        } else {
            Log.w(TAG, "No text extracted from WhatsApp")
        }
    }

    /**
     * Recursively extract text from accessibility nodes
     */
    private fun extractTextFromNode(node: AccessibilityNodeInfo?, texts: MutableList<String>) {
        if (node == null) return

        // Check if this node contains message text
        val text = node.text?.toString()
        if (!text.isNullOrBlank() && isMessageText(node)) {
            texts.add(text.trim())
        }

        // Check content description as fallback
        val contentDesc = node.contentDescription?.toString()
        if (!contentDesc.isNullOrBlank() && isMessageText(node)) {
            texts.add(contentDesc.trim())
        }

        // Recursively check child nodes
        for (i in 0 until node.childCount) {
            extractTextFromNode(node.getChild(i), texts)
        }
    }

    /**
     * Determine if a node likely contains message text
     */
    private fun isMessageText(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString() ?: ""
        val resourceId = node.viewIdResourceName ?: ""
        
        // Look for TextView or similar components that typically contain messages
        return className.contains("TextView") || 
               className.contains("EditText") ||
               resourceId.contains("message") ||
               resourceId.contains("text") ||
               resourceId.contains("chat")
    }

    /**
     * Get the currently selected text (if user selected text before tapping overlay)
     */
    fun getSelectedText(): String? {
        val rootNode = rootInActiveWindow ?: return null
        
        val selectedTexts = mutableListOf<String>()
        findSelectedText(rootNode, selectedTexts)
        
        return if (selectedTexts.isNotEmpty()) {
            selectedTexts.joinToString(" ")
        } else null
    }

    private fun findSelectedText(node: AccessibilityNodeInfo?, selectedTexts: MutableList<String>) {
        if (node == null) return

        if (node.isSelected || node.isFocused) {
            val text = node.text?.toString()
            if (!text.isNullOrBlank()) {
                selectedTexts.add(text.trim())
            }
        }

        for (i in 0 until node.childCount) {
            findSelectedText(node.getChild(i), selectedTexts)
        }
    }
}