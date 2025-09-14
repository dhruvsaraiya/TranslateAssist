package com.translateassist.translation

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class TranslationEngine(private val context: Context) {

    companion object {
        private const val TAG = "TranslationEngine"
    }

    private var englishToGujaratiTranslator: Translator? = null
    private val languageIdentifier = LanguageIdentification.getClient()
    private val transliterator = Transliterator()

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
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        englishToGujaratiTranslator?.downloadModelIfNeeded(conditions)
            ?.addOnSuccessListener {
                Log.d(TAG, "English to Gujarati translation model downloaded")
            }
            ?.addOnFailureListener { exception ->
                Log.e(TAG, "Failed to download translation model", exception)
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
        val languageCode = identifyLanguage(text)
        Log.d(TAG, "Detected language: $languageCode for line: ${text.take(50)}")
        return when {
            languageCode == TranslateLanguage.ENGLISH -> processEnglishLine(text)
            languageCode == TranslateLanguage.GUJARATI -> TranslationResult(
                originalText = text,
                translatedText = text,
                detectedLanguage = "",
                translationType = "Original",
                linePairs = listOf(TranslationLinePair(text, null, null, LineMode.ORIGINAL))
            )
            else -> {
                val translated = suspendCoroutine<String?> { continuation ->
                    englishToGujaratiTranslator?.translate(text)
                        ?.addOnSuccessListener { result -> continuation.resume(result) }
                        ?.addOnFailureListener { continuation.resume(null) }
                }
                TranslationResult(
                    originalText = text,
                    translatedText = translated ?: "Translation not supported",
                    detectedLanguage = "",
                    translationType = "Translated",
                    linePairs = listOf(TranslationLinePair(text, translated ?: "Translation not supported", null, LineMode.TRANSLATED))
                )
            }
        }
    }
    private suspend fun processEnglishLine(text: String): TranslationResult {
        Log.d(TAG, "EN line start | original='${text.take(200)}'")
        val translationOut = suspendCoroutine<String?> { continuation ->
            englishToGujaratiTranslator?.translate(text)
                ?.addOnSuccessListener { result -> continuation.resume(result) }
                ?.addOnFailureListener { continuation.resume(null) }
        } ?: "Translation failed"
        Log.d(TAG, "EN line translation complete | originalSnippet='${text.take(60)}' | translation='${translationOut.take(200)}'")
        val transliterationOut = transliterator.transliterateToGujarati(text)
        Log.d(TAG, "EN line transliteration attempt | originalSnippet='${text.take(60)}' | transliteration='${transliterationOut?.take(200)}'")
        val chosenMode = if (!transliterationOut.isNullOrBlank()) LineMode.TRANSLITERATED else LineMode.TRANSLATED
        val chosenText = if (chosenMode == LineMode.TRANSLITERATED) transliterationOut!! else translationOut
        Log.d(TAG, "EN line decision | mode=$chosenMode | chosen='${chosenText.take(200)}'")
        return TranslationResult(
            originalText = text,
            translatedText = chosenText,
            detectedLanguage = "",
            translationType = if (chosenMode == LineMode.TRANSLITERATED) "Both" else "Translated",
            linePairs = listOf(TranslationLinePair(text, translationOut, transliterationOut, chosenMode))
        )
    }

    private suspend fun identifyLanguage(text: String): String {
        return try {
            val languageCode = suspendCoroutine<String> { continuation ->
                languageIdentifier.identifyLanguage(text)
                    .addOnSuccessListener { result -> continuation.resume(result) }
                    .addOnFailureListener { continuation.resume("und") }
            }
            if (languageCode == "und") TranslateLanguage.ENGLISH else languageCode
        } catch (e: Exception) {
            Log.e(TAG, "Language identification failed", e)
            TranslateLanguage.ENGLISH
        }
    }



    fun cleanup() {
        englishToGujaratiTranslator?.close()
        languageIdentifier.close()
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