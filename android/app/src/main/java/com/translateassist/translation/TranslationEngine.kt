package com.translateassist.translation

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class TranslationEngine(private val context: Context) {

    companion object {
        private const val TAG = "TranslationEngine"
    }

    private var englishToGujaratiTranslator: Translator? = null
    private var languageIdentifier = LanguageIdentification.getClient()
    private val transliterator = Transliterator()
    private val modelMutex = Mutex()
    private var modelReady: Boolean = false
    private var didForceRedownloadOnce: Boolean = false

    init {
        initializeTranslators()
    }

    private fun initializeTranslators() {
        // English to Gujarati translator
        val englishToGujaratiOptions = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.GUJARATI)
            .build()
        englishToGujaratiTranslator = Translation.getClient(englishToGujaratiOptions)

        // Download translation models
        downloadModels()
    }

    private fun downloadModels() {
        // Default to allowing any network. On some devices a Wi-Fi-only requirement results in a
        // model that never finishes downloading, and translation then fails at runtime.
        val conditions = DownloadConditions.Builder().build()

        englishToGujaratiTranslator?.downloadModelIfNeeded(conditions)
            ?.addOnSuccessListener {
                modelReady = true
                Log.d(TAG, "English↔Gujarati translation model ready")
            }
            ?.addOnFailureListener { exception ->
                modelReady = false
                Log.e(TAG, "Failed to download translation model", exception)
            }
    }

    private suspend fun ensureModelReady(): Boolean {
        val translator = englishToGujaratiTranslator ?: run {
            initializeTranslators()
            englishToGujaratiTranslator
        } ?: return false

        if (modelReady) return true

        return modelMutex.withLock {
            if (modelReady) return@withLock true
            try {
                val conditions = DownloadConditions.Builder().build()
                // Don't block the whole pipeline indefinitely. If the model isn't ready quickly,
                // we let transliteration proceed and translation can succeed later.
                val ok = withTimeoutOrNull(1500L) {
                    awaitTaskCancellable(translator.downloadModelIfNeeded(conditions))
                    true
                } ?: false
                modelReady = ok
                if (!ok) Log.w(TAG, "Model not ready yet (timeout); will retry later")
                ok
            } catch (e: Exception) {
                modelReady = false
                Log.e(TAG, "Model download/ready check failed", e)
                false
            }
        }
    }

    private suspend fun forceRedownloadModels() {
        modelMutex.withLock {
            modelReady = false
            val manager = RemoteModelManager.getInstance()
            try {
                // Delete both language models to ensure the bilingual pack is rebuilt cleanly.
                withTimeoutOrNull(4000L) {
                    awaitTaskCancellable(manager.deleteDownloadedModel(TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build()))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete EN model (non-fatal)", e)
            }
            try {
                withTimeoutOrNull(4000L) {
                    awaitTaskCancellable(manager.deleteDownloadedModel(TranslateRemoteModel.Builder(TranslateLanguage.GUJARATI).build()))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete GU model (non-fatal)", e)
            }

            // Recreate translator and download again.
            initializeTranslators()
            val translator = englishToGujaratiTranslator ?: return@withLock
            val conditions = DownloadConditions.Builder().build()
            val ok = withTimeoutOrNull(8000L) {
                awaitTaskCancellable(translator.downloadModelIfNeeded(conditions))
                true
            } ?: false
            modelReady = ok
        }
    }

    private suspend fun <T> awaitTaskCancellable(task: Task<T>): T = suspendCancellableCoroutine { continuation ->
        task.addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }.addOnFailureListener { exception ->
            if (continuation.isActive) continuation.resumeWithException(exception)
        }
    }

    suspend fun translateText(text: String): TranslationResult {
        return try {
            if (!text.contains('\n')) {
                processSingle(text)
            } else {
                processMultiLine(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Translation error", e)
            TranslationResult(
                originalText = text,
                translatedText = "Translation error: ${e.message}",
                detectedLanguage = "Unknown",
                translationType = "Error"
            )
        }
    }

    /**
     * Streaming version: invokes onPair for each processed line pair as soon as it's ready.
     * Returns the full aggregated TranslationResult at the end.
     */
    suspend fun translateTextStreaming(text: String, onPair: (TranslationLinePair) -> Unit): TranslationResult {
        return try {
            if (!text.contains('\n')) {
                val single = processSingle(text)
                single.linePairs.firstOrNull()?.let { onPair(it) }
                single
            } else {
                val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) return processSingle(text)
                val collectedPairs = mutableListOf<TranslationLinePair>()
                val translatedAggregated = mutableListOf<String>()
                var hasOriginalOnly = true
                for (line in lines) {
                    val child = processSingle(line)
                    val pair = child.linePairs.firstOrNull() ?: TranslationLinePair(line, child.translatedText, null, LineMode.TRANSLATED)
                    collectedPairs += pair
                    translatedAggregated += child.translatedText
                    if (child.translationType != "Original") hasOriginalOnly = false
                    // Emit incrementally
                    onPair(pair)
                }
                val combined = translatedAggregated.joinToString("\n")
                val typeSummary = if (hasOriginalOnly) "Original" else "Mixed"
                TranslationResult(
                    originalText = text,
                    translatedText = combined,
                    detectedLanguage = "",
                    translationType = typeSummary,
                    linePairs = collectedPairs
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Streaming translation error", e)
            TranslationResult(
                originalText = text,
                translatedText = "Translation error: ${e.message}",
                detectedLanguage = "Unknown",
                translationType = "Error"
            )
        }
    }

    private suspend fun processMultiLine(text: String): TranslationResult {
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return processSingle(text)
        val collectedPairs = mutableListOf<TranslationLinePair>()
        val translatedAggregated = mutableListOf<String>()
        var hasOriginalOnly = true
        for (line in lines) {
            val child = processSingle(line)
            translatedAggregated += child.translatedText
            // child.linePairs should contain exactly one entry per our construction
            if (child.linePairs.isNotEmpty()) {
                collectedPairs += child.linePairs.first()
            } else {
                collectedPairs += TranslationLinePair(line, child.translatedText, null, LineMode.TRANSLATED)
            }
            if (child.translationType != "Original") hasOriginalOnly = false
        }
        val combinedTranslated = translatedAggregated.joinToString("\n")
        val typeSummary = when {
            hasOriginalOnly -> "Original"
            else -> "Mixed"
        }
        return TranslationResult(
            originalText = text,
            translatedText = combinedTranslated,
            detectedLanguage = "",
            translationType = typeSummary,
            linePairs = collectedPairs
        )
    }

    private fun summarizeTypes(types: List<String>): String {
        val counts = types.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
        return when (counts.size) {
            0 -> "Unknown"
            1 -> counts.first().first + " (all)"
            else -> counts.joinToString("; ") { (t, c) -> "$t x$c" }
        }
    }

    private suspend fun processSingle(text: String): TranslationResult {
        ensureLanguageIdentifier()
        val languageCode = identifyLanguage(text)
        val hasLatin = text.any { (it in 'a'..'z') || (it in 'A'..'Z') }
        val hasGujarati = text.any { ch -> ch.code in 0x0A80..0x0AFF }
        Log.d(TAG, "Detected language: $languageCode | hasLatin=$hasLatin | hasGujarati=$hasGujarati | snippet='${text.take(50)}'")

        // Rule: If it has Latin letters (and not Gujarati script), always treat as an English-origin line
        // so we perform BOTH Translation (EN->GU) and Transliteration attempts.
        if (hasLatin && !hasGujarati) {
            return processEnglishLine(text)
        }

        // Pure Gujarati script (or mixed containing Gujarati) -> leave as original (no translation)
        if (hasGujarati) {
            return TranslationResult(
                originalText = text,
                translatedText = text,
                detectedLanguage = languageCode,
                translationType = "Original",
                linePairs = listOf(TranslationLinePair(text, null, null, LineMode.ORIGINAL))
            )
        }

        // Fallback for other scripts: attempt translation (will often be unsupported for non-EN sources)
    val translated = safeTranslate(text)
        return TranslationResult(
            originalText = text,
            translatedText = translated ?: "Translation not supported",
            detectedLanguage = languageCode,
            translationType = "Translated",
            linePairs = listOf(TranslationLinePair(text, translated ?: "Translation not supported", null, LineMode.TRANSLATED))
        )
    }
    private suspend fun processEnglishLine(text: String): TranslationResult {
        Log.d(TAG, "EN line start | original='${text.take(200)}'")
        // TR and TL must be independent: failure/timeout of one must not block the other.
        val (translationOut, transliterationOut) = supervisorScope {
            val trDeferred = async {
                // TR: ML Kit path (may depend on model download)
                withTimeoutOrNull(6500L) { safeTranslate(text) }
            }
            val tlDeferred = async {
                // TL: network phonetic transliteration
                withTimeoutOrNull(6500L) { transliterator.transliterateToGujarati(text) }
            }
            val tr = runCatching { trDeferred.await() }.getOrNull()
            val tl = runCatching { tlDeferred.await() }.getOrNull()
            Pair(tr, tl)
        }

        Log.d(TAG, "EN line TR complete | originalSnippet='${text.take(60)}' | tr='${translationOut?.take(200)}'")
        Log.d(TAG, "EN line TL complete | originalSnippet='${text.take(60)}' | tl='${transliterationOut?.take(200)}'")

        val chosenMode = when {
            !transliterationOut.isNullOrBlank() -> LineMode.TRANSLITERATED
            !translationOut.isNullOrBlank() -> LineMode.TRANSLATED
            else -> LineMode.ORIGINAL
        }
        val chosenText = when (chosenMode) {
            LineMode.TRANSLITERATED -> transliterationOut!!
            LineMode.TRANSLATED -> translationOut!!
            LineMode.ORIGINAL -> text
        }
        Log.d(TAG, "EN line decision | mode=$chosenMode | chosen='${chosenText.take(200)}'")
        return TranslationResult(
            originalText = text,
            translatedText = chosenText,
            detectedLanguage = "",
            translationType = when {
                !translationOut.isNullOrBlank() && !transliterationOut.isNullOrBlank() -> "Both"
                !translationOut.isNullOrBlank() -> "Translated"
                !transliterationOut.isNullOrBlank() -> "Transliterated"
                else -> "Original"
            },
            linePairs = listOf(TranslationLinePair(text, translationOut, transliterationOut, chosenMode))
        )
    }

    private suspend fun identifyLanguage(text: String): String {
        return try {
            val languageCode = suspendCoroutine<String> { continuation ->
                try {
                    languageIdentifier.identifyLanguage(text)
                        .addOnSuccessListener { result -> continuation.resume(result) }
                        .addOnFailureListener { continuation.resume("und") }
                } catch (e: IllegalStateException) {
                    // Re-init and fallback
                    Log.w(TAG, "LanguageIdentifier was closed; recreating")
                    languageIdentifier = LanguageIdentification.getClient()
                    continuation.resume("und")
                }
            }
            if (languageCode == "und") TranslateLanguage.ENGLISH else languageCode
        } catch (e: Exception) {
            Log.w(TAG, "Language identification failed (soft)", e)
            TranslateLanguage.ENGLISH
        }
    }

    // Ensure languageIdentifier not closed
    private fun ensureLanguageIdentifier() {
        // No direct API to check closed state; we lazily recreate on IllegalStateException inside identifyLanguage.
        // Placeholder if future explicit state tracking is needed.
    }

    // Safe translation with one retry if translator was closed
    private suspend fun safeTranslate(text: String): String? {
        ensureTranslator()
        var attempt = 0
        var last: String? = null
        while (attempt < 2) { // try at most twice (initial + 1 re-init)
            if (!ensureModelReady()) {
                Log.w(TAG, "Translation model not ready; skipping translation")
                return null
            }

            val result = withTimeoutOrNull(6000L) {
                suspendCancellableCoroutine<String?> { continuation ->
                    try {
                        englishToGujaratiTranslator?.translate(text)
                            ?.addOnSuccessListener { r ->
                                if (continuation.isActive) continuation.resume(r)
                            }
                            ?.addOnFailureListener { e ->
                                Log.w(TAG, "ML Kit translate() failed: ${e.message}", e)
                                if (continuation.isActive) continuation.resume(null)
                            }
                    } catch (e: IllegalStateException) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }

            if (result != null) return result

            // Recovery: if the model on disk is corrupted/incomplete, delete and re-download once.
            if (!didForceRedownloadOnce) {
                didForceRedownloadOnce = true
                try {
                    Log.w(TAG, "Translation failed; forcing model re-download once")
                    forceRedownloadModels()
                    // Retry in the next loop iteration
                } catch (e: Exception) {
                    Log.e(TAG, "Force re-download failed", e)
                }
            }

            // If translator might be closed, re-init and retry
            attempt++
            if (attempt < 2) {
                Log.w(TAG, "Reinitializing translator after failure/closed state (attempt=$attempt)")
                initializeTranslators()
            }
            last = result
        }
        return last
    }

    private fun ensureTranslator() {
        if (englishToGujaratiTranslator == null) {
            initializeTranslators()
        }
    }



    fun cleanup() {
        try { englishToGujaratiTranslator?.close() } catch (_: Exception) {}
        try { languageIdentifier.close() } catch (_: Exception) {}
    }
}

enum class LineMode { TRANSLATED, TRANSLITERATED, ORIGINAL }

data class TranslationLinePair(
    val original: String,
    val translation: String?,
    val transliteration: String?,
    val chosenMode: LineMode
) {
    fun chosenText(): String = when (chosenMode) {
        LineMode.TRANSLITERATED -> transliteration ?: translation ?: original
        LineMode.TRANSLATED -> translation ?: transliteration ?: original
        LineMode.ORIGINAL -> original
    }
}

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val detectedLanguage: String,
    val translationType: String,
    val linePairs: List<TranslationLinePair> = emptyList()
)