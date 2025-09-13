package com.translateassist.translation

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.android.gms.tasks.Tasks
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class TranslationEngine(private val context: Context) {

    companion object {
        private const val TAG = "TranslationEngine"
        private const val CONFIDENCE_THRESHOLD = 0.5f
    }

    private var englishToGujaratiTranslator: Translator? = null
    private val languageIdentifier = LanguageIdentification.getClient()
    private val indicTransliterator = IndicTransTransliterator()

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
        try {
            // First, identify the language
            val languageCode = identifyLanguage(text)
            Log.d(TAG, "Detected language: $languageCode for text: ${text.take(50)}")

            return when {
                languageCode == TranslateLanguage.ENGLISH -> {
                    // Translate English to Gujarati
                    val translated = suspendCoroutine<String?> { continuation ->
                        englishToGujaratiTranslator?.translate(text)
                            ?.addOnSuccessListener { result -> continuation.resume(result) }
                            ?.addOnFailureListener { continuation.resume(null) }
                    }
                    TranslationResult(
                        originalText = text,
                        translatedText = translated ?: "Translation failed",
                        detectedLanguage = "English",
                        translationType = "English → Gujarati"
                    )
                }
                languageCode == TranslateLanguage.GUJARATI -> {
                    // Text is already in Gujarati
                    TranslationResult(
                        originalText = text,
                        translatedText = text,
                        detectedLanguage = "Gujarati",
                        translationType = "Already in Gujarati"
                    )
                }
                indicTransliterator.isRomanGujarati(text) -> {
                    // Handle Roman Gujarati using IndicTrans
                    val gujaratiScript = indicTransliterator.transliterateRomanToGujarati(text)
                    TranslationResult(
                        originalText = text,
                        translatedText = gujaratiScript,
                        detectedLanguage = "Roman Gujarati",
                        translationType = "Roman → Gujarati (IndicTrans)"
                    )
                }
                else -> {
                    // Try to translate from detected language to Gujarati
                    val translated = suspendCoroutine<String?> { continuation ->
                        englishToGujaratiTranslator?.translate(text)
                            ?.addOnSuccessListener { result -> continuation.resume(result) }
                            ?.addOnFailureListener { continuation.resume(null) }
                    }
                    TranslationResult(
                        originalText = text,
                        translatedText = translated ?: "Translation not supported",
                        detectedLanguage = languageCode,
                        translationType = "$languageCode → Gujarati"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Translation error", e)
            return TranslationResult(
                originalText = text,
                translatedText = "Translation error: ${e.message}",
                detectedLanguage = "Unknown",
                translationType = "Error"
            )
        }
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

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val detectedLanguage: String,
    val translationType: String
)