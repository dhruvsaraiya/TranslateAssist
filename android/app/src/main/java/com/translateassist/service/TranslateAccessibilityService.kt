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

    // Stateless mode: no persistent tracking of prior messages; each invocation processes what's visible.

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
     * Extract text from current app when overlay button is tapped
     */
    fun extractTextFromWhatsApp() {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.w(TAG, "Root node is null")
            onTextExtracted?.invoke("Error: Could not access current app")
            return
        }

        val packageName = rootNode.packageName?.toString()
        Log.d(TAG, "Current app package: $packageName")
        
        // Allow WhatsApp and common messaging apps for testing
        val supportedApps = listOf(
            "com.whatsapp",
            "com.google.android.apps.messaging", 
            "com.android.mms",
            "com.samsung.android.messaging"
        )
        
        if (packageName !in supportedApps) {
            Log.w(TAG, "App not supported: $packageName")
            onTextExtracted?.invoke("Error: Please open WhatsApp or Messages app")
            return
        }

        val allTexts = mutableListOf<String>()
        
        // Extract ALL text for debugging
        extractAllText(rootNode, allTexts)
        
        Log.d(TAG, "Found ${allTexts.size} text elements total")
        allTexts.forEachIndexed { index, text -> 
            Log.d(TAG, "All Text $index: '$text'")
        }
        
        // Log count for immediate feedback
        Log.i(TAG, "=== ACCESSIBILITY DEBUG: Found ${allTexts.size} text elements ===")

        if (allTexts.isNotEmpty()) {
            // Filter for actual message content
            val messageTexts = filterMessageContent(allTexts)
            
            Log.d(TAG, "Filtered to ${messageTexts.size} message texts: ${messageTexts.take(3)}")
            
            if (messageTexts.isNotEmpty()) {
                // Deduplicate within this invocation only, preserve original order of appearance.
                val seen = LinkedHashSet<String>()
                val uniqueOrdered = messageTexts.filter { seen.add(it) }
                // Optionally cap to avoid overly large payloads; keep last N for relevance.
                val MAX_VISIBLE_SEND = 8
                val windowed = if (uniqueOrdered.size > MAX_VISIBLE_SEND) uniqueOrdered.takeLast(MAX_VISIBLE_SEND) else uniqueOrdered
                val payload = windowed.joinToString("\n")
                Log.d(TAG, "Sending VISIBLE messages (${windowed.size}/${uniqueOrdered.size} unique): $payload")
                onTextExtracted?.invoke(payload)
            } else {
                // Fallback - show debug info if no messages found
                val debugText = "No clear messages found. All texts:\n" + 
                               allTexts.take(8).joinToString("\n") { "• $it" }
                onTextExtracted?.invoke(debugText)
            }
        } else {
            Log.w(TAG, "No text extracted from app")
            onTextExtracted?.invoke("No text found in current screen. Try scrolling to see messages or select specific text.")
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
            val trimmedText = text.trim()
            // Avoid duplicates and ensure meaningful content
            if (trimmedText !in texts && trimmedText.length > 2) {
                texts.add(trimmedText)
                Log.d(TAG, "Added text: '$trimmedText' from ${node.className}")
            }
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
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        
        // Skip common UI labels and hints
        val skipTexts = listOf(
            "text message", "send", "type a message", "compose", "camera", "attach",
            "more options", "back", "search", "call", "video call", "info",
            "emoji", "voice message", "gallery", "contact", "location"
        )
        
        if (skipTexts.any { skip -> 
            text.lowercase().contains(skip) || contentDesc.lowercase().contains(skip)
        }) {
            return false
        }
        
        // Skip very short text (likely UI elements)
        if (text.length < 3) {
            return false
        }
        
        // Skip text that looks like UI elements (all caps, single words)
        if (text.all { it.isUpperCase() || !it.isLetter() } && text.length < 10) {
            return false
        }
        
        // Look for TextView with meaningful content
        val isTextView = className.contains("TextView")
        val hasMessageContent = text.length > 3 && text.any { it.isLetter() }
        val isInMessageArea = resourceId.contains("message") || 
                             resourceId.contains("text") || 
                             resourceId.contains("content") ||
                             resourceId.contains("body")
        
        return isTextView && hasMessageContent && (isInMessageArea || text.length > 10)
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
    
    /**
     * Look for specific message container patterns in messaging apps
     */
    private fun findMessageContainers(node: AccessibilityNodeInfo?, texts: MutableList<String>) {
        if (node == null) return
        
        val resourceId = node.viewIdResourceName ?: ""
        val className = node.className?.toString() ?: ""
        
        // Look for known message container patterns
        val messageContainerPatterns = listOf(
            "message_text", "msg_text", "text_content", "bubble_text",
            "conversation_text", "chat_message", "message_body",
            "text_view_message", "message_content"
        )
        
        val isMessageContainer = messageContainerPatterns.any { pattern ->
            resourceId.contains(pattern, ignoreCase = true)
        }
        
        if (isMessageContainer) {
            val text = node.text?.toString()
            if (!text.isNullOrBlank() && text.length > 3) {
                Log.d(TAG, "Found message container: $resourceId with text: ${text.take(50)}")
                texts.add(text.trim())
            }
        }
        
        // Also check for RecyclerView or ListView items that might contain messages
        if (className.contains("TextView") && resourceId.isNotEmpty()) {
            val text = node.text?.toString()
            if (!text.isNullOrBlank() && 
                text.length > 10 && 
                !text.lowercase().contains("text message") &&
                !text.lowercase().contains("type a message")) {
                
                // This might be actual message content
                Log.d(TAG, "Found potential message: $resourceId - ${text.take(50)}")
                texts.add(text.trim())
            }
        }
        
        // Recursively check child nodes
        for (i in 0 until node.childCount) {
            findMessageContainers(node.getChild(i), texts)
        }
    }
    
    /**
     * Extract ALL text for debugging purposes
     */
    private fun extractAllText(node: AccessibilityNodeInfo?, texts: MutableList<String>) {
        if (node == null) return
        
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) {
            val trimmedText = text.trim()
            if (trimmedText.isNotEmpty() && trimmedText !in texts) {
                texts.add(trimmedText)
            }
        }
        
        val contentDesc = node.contentDescription?.toString()
        if (!contentDesc.isNullOrBlank() && contentDesc != text) {
            val trimmedDesc = contentDesc.trim()
            if (trimmedDesc.isNotEmpty() && trimmedDesc !in texts) {
                texts.add("CD: $trimmedDesc") // Mark content descriptions
            }
        }
        
        // Recursively check child nodes
        for (i in 0 until node.childCount) {
            extractAllText(node.getChild(i), texts)
        }
    }
    
    /**
     * Filter out UI elements and keep only actual message content
     */
    private fun filterMessageContent(allTexts: List<String>): List<String> {
        return allTexts.map { it.replace("\u200C", "").trim() } // remove zero-width non-joiner, trim
            .filter { text ->
                if (text.isBlank()) return@filter false
                // Skip content descriptions (marked with "CD:")
                if (text.startsWith("CD:")) return@filter false

                val lower = text.lowercase()

                // Skip very short (except allow common acknowledgements like ok, હા, ઓકે, etc.)
                val shortAllow = setOf("ok", "okk", "haan", "ha", "yes", "na", "no")
                if (text.length < 2) return@filter false
                if (text.length < 3 && lower !in shortAllow) return@filter false

                // UI / structural patterns
                val skipContains = listOf(
                    "text message", "type a message", "compose", "attach", "more options",
                    "back", "search", "call", "video call", "emoji", "voice message",
                    "gallery", "contact", "location", "thumbs up", "reaction", "texting with",
                    "sms/mms", "show attach", "you said", "delivered", "read", "typing",
                    "only admins can send messages"
                )
                if (skipContains.any { lower.contains(it) }) return@filter false

                // Exact equals / metadata tokens
                val skipExact = setOf("you", "message", "yesterday", "today")
                if (lower in skipExact) return@filter false

                // Participants summary (e.g., "35 others") or line mentioning "others"
                if (lower.matches(Regex(".*\\bothers\\b.*"))) return@filter false

                // Group presence indicators: "2 online", "3 online" etc.
                if (lower.matches(Regex("^\\d+\\s+online$"))) return@filter false

                // Media size indicators (e.g., 20 KB, 3 MB) which correspond to image/file placeholders
                if (lower.matches(Regex("^\\d+(?:\\.\\d+)?\\s?(kb|mb|gb)$"))) return@filter false

                // Page count lines (e.g., "4 pages", "1 page")
                if (lower.matches(Regex("^\\d+\\s+pages?$"))) return@filter false

                // File attachment names with common extensions (avoid treating as message text)
                if (lower.matches(Regex("^.+\\.(pdf|docx?|pptx?|xls[xm]?|jpg|jpeg|png|gif|mp4|mp3|zip|rar)$"))) return@filter false

                // All-caps UI/command labels (length >4 to avoid removing short ACKs like OK, YES)
                if (text.length > 4 && text.none { it.isLowerCase() } && text.any { it.isLetter() } && !text.any { ch -> ch.code in 0x0A80..0x0AFF }) {
                    // Permit if it contains Gujarati script (handled above) else skip
                    return@filter false
                }

                // Phone numbers / contacts (international / local patterns)
                if (text.matches(Regex("^\\+?\\d[\\d\\s-]{6,}$"))) return@filter false
                // WhatsApp sometimes formats numbers with spaces: +91 12345 67890
                if (text.matches(Regex("^\\+?\\d{2,3}\\s?\\d{3,5}\\s?\\d{3,5}"))) return@filter false

                // Line starting with tilde or hyphen indicating author label: "~ Dhruv Saraiya"
                if (text.startsWith("~") || text.startsWith("- ")) return@filter false

                // Time pattern (09:13 PM / 9:13)
                if (text.matches(Regex("^\\d{1,2}:\\d{2}(?:\\s?(?:AM|PM|am|pm))?$"))) return@filter false

                // Predominantly digits/punctuation
                val letters = text.count { it.isLetter() }
                val digits = text.count { it.isDigit() }
                if (letters == 0) return@filter false
                if (letters > 0 && digits > 0 && digits > letters * 2) return@filter false

                // All caps short tokens (likely UI) e.g., GIF, JPG
                if (text.all { !it.isLetter() || it.isUpperCase() } && text.length <= 4) return@filter false

                true
            }
    }

    // (Stateless) filterNewMessages removed.
}