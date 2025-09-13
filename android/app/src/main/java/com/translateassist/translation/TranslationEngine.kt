package com.translateassist.translation

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

class TranslationEngine(private val context: Context) {

    companion object {
        private const val TAG = "TranslationEngine"
        private const val CONFIDENCE_THRESHOLD = 0.5f
    }

    private var englishToGujaratiTranslator: Translator? = null
    private val languageIdentifier = LanguageIdentification.getClient()

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
                    val translated = englishToGujaratiTranslator?.translate(text)?.await()
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
                isRomanGujarati(text) -> {
                    // Handle Roman Gujarati (Gujarati written in English letters)
                    val gujaratiScript = transliterateRomanToGujarati(text)
                    TranslationResult(
                        originalText = text,
                        translatedText = gujaratiScript,
                        detectedLanguage = "Roman Gujarati",
                        translationType = "Roman → Gujarati Script"
                    )
                }
                else -> {
                    // Try to translate from detected language to Gujarati
                    val translated = englishToGujaratiTranslator?.translate(text)?.await()
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
            val languageCode = languageIdentifier.identifyLanguage(text).await()
            if (languageCode == "und") TranslateLanguage.ENGLISH else languageCode
        } catch (e: Exception) {
            Log.e(TAG, "Language identification failed", e)
            TranslateLanguage.ENGLISH
        }
    }

    private fun isRomanGujarati(text: String): Boolean {
        // Simple heuristic to detect Roman Gujarati
        // Look for common Gujarati words written in Roman script
        val romanGujaratiPatterns = listOf(
            "tame", "ame", "che", "chhe", "thi", "ma", "ne", "to", "pan", "hoy",
            "kya", "kai", "kevi", "kem", "kyare", "kya", "su", "saru", "biju",
            "mari", "maru", "tari", "taru", "amari", "amaru", "tamari", "tamaru"
        )

        val lowercaseText = text.lowercase()
        return romanGujaratiPatterns.any { pattern ->
            lowercaseText.contains(pattern)
        }
    }

    private fun transliterateRomanToGujarati(romanText: String): String {
        // Basic Roman to Gujarati transliteration map
        val transliterationMap = mapOf(
            // Vowels
            "a" to "અ", "aa" to "આ", "i" to "ઇ", "ii" to "ઈ", "u" to "ઉ", "uu" to "ઊ",
            "e" to "એ", "ai" to "ઐ", "o" to "ઓ", "au" to "ઔ",
            
            // Consonants
            "ka" to "ક", "kha" to "ખ", "ga" to "ગ", "gha" to "ઘ", "nga" to "ઙ",
            "cha" to "ચ", "chha" to "છ", "ja" to "જ", "jha" to "ઝ", "nja" to "ઞ",
            "ta" to "ટ", "tha" to "ઠ", "da" to "ડ", "dha" to "ઢ", "na" to "ણ",
            "pa" to "પ", "pha" to "ફ", "ba" to "બ", "bha" to "ભ", "ma" to "મ",
            "ya" to "ય", "ra" to "ર", "la" to "લ", "va" to "વ", "sha" to "શ",
            "sa" to "સ", "ha" to "હ", "ksha" to "ક્ષ", "gya" to "જ્ઞ",
            
            // Common words
            "tame" to "તમે", "ame" to "અમે", "che" to "છે", "chhe" to "છે",
            "thi" to "થી", "ma" to "માં", "ne" to "ને", "to" to "તો", "pan" to "પણ",
            "hoy" to "હોય", "kya" to "ક્યા", "kai" to "કઈ", "kem" to "કેમ",
            "su" to "શું", "saru" to "સારું", "mari" to "મારી", "maru" to "મારું"
        )

        var result = romanText.lowercase()
        
        // Apply transliteration (longer patterns first to avoid partial matches)
        transliterationMap.entries.sortedByDescending { it.key.length }.forEach { (roman, gujarati) ->
            result = result.replace(roman, gujarati)
        }
        
        return result
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