package com.translateassist.translation

import android.util.Log
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Path
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.annotations.SerializedName

/**
 * Data classes for AI4Bharat API
 */
data class TransliterationRequest(
    @SerializedName("input")
    val input: List<TransliterationInput>,
    @SerializedName("config")
    val config: TransliterationConfig
)

data class TransliterationInput(
    @SerializedName("source")
    val source: String
)

data class TransliterationConfig(
    @SerializedName("language")
    val language: Language,
    @SerializedName("isSentence")
    val isSentence: Boolean = false,
    @SerializedName("numSuggestions")
    val numSuggestions: Int = 5
)

data class Language(
    @SerializedName("sourceLanguage")
    val sourceLanguage: String,
    @SerializedName("targetLanguage")
    val targetLanguage: String
)

data class TransliterationResponse(
    @SerializedName("output")
    val output: List<TransliterationOutput>
)

data class TransliterationOutput(
    @SerializedName("source")
    val source: String,
    @SerializedName("target")
    val target: List<String>
)

// Alternative simple API response format
data class SimpleTransliterationResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("result")
    val result: Map<String, List<String>>?,
    @SerializedName("error")
    val error: String?
)

/**
 * Retrofit interface for AI4Bharat transliteration API
 */
interface AI4BharatTransliterationAPI {
    @POST("transliterate")
    suspend fun transliterate(@Body request: TransliterationRequest): TransliterationResponse
    
    @GET("tl/{langCode}/{word}")
    suspend fun transliterateWord(
        @Path("langCode") langCode: String,
        @Path("word") word: String
    ): SimpleTransliterationResponse
}

/**
 * Roman to Gujarati transliteration using AI4Bharat APIs
 */
class IndicTransTransliterator(private val appContext: Context? = null) {
    
    companion object {
        private const val TAG = "IndicTransliterator"
        private const val BASE_URL = "https://xlit-api.ai4bharat.org/"
        private const val GUJARATI_CODE = "gu"
    }
    
    private val api: AI4BharatTransliterationAPI by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AI4BharatTransliterationAPI::class.java)
    }
    
    /**
     * Transliterate Roman text to Gujarati script using offline Python (if available) or AI4Bharat APIs.
     * Fallback order (manual heuristic removed):
     * 1. Embedded Python engine (Chaquopy + ai4bharat-transliteration model)
     * 2. AI4Bharat batch POST API
     * 3. AI4Bharat word-by-word GET API
     * 4. If all fail: return original text unchanged.
     */
    suspend fun transliterateRomanToGujarati(romanText: String): String {
        Log.d(TAG, "Transliterating Roman to Gujarati (offline->API fallback): $romanText")
        if (romanText.isBlank()) return romanText

        // 0. Attempt embedded Python engine (if context provided)
        if (appContext != null) {
            try {
                val pythonEngine = PythonTransliterator(appContext)
                val offline = withContext(Dispatchers.IO) { pythonEngine.transliterate(romanText) }
                // Heuristic: if offline result contains Gujarati script, accept it.
                if (offline.any { it.code in 0x0A80..0x0AFF }) {
                    return offline
                }
            } catch (e: Exception) {
                Log.w(TAG, "Offline Python transliteration failed or unavailable", e)
            }
        }

        return try {
            transliterateUsingBatchAPI(romanText)
        } catch (e: Exception) {
            Log.w(TAG, "Batch API failed, trying word-by-word API", e)
            try {
                transliterateWordByWord(romanText)
            } catch (e2: Exception) {
                Log.e(TAG, "Both APIs failed, returning original text", e2)
                romanText
            }
        }
    }
    
    /**
     * Use the batch POST API for transliteration
     */
    private suspend fun transliterateUsingBatchAPI(text: String): String {
        val words = text.trim().split("\\s+".toRegex())
        if (words.isEmpty()) return text
        
        val request = TransliterationRequest(
            input = words.map { TransliterationInput(it) },
            config = TransliterationConfig(
                language = Language(
                    sourceLanguage = "en",
                    targetLanguage = GUJARATI_CODE
                ),
                isSentence = false,
                numSuggestions = 1
            )
        )
        
        val response = api.transliterate(request)
        
        return response.output.joinToString(" ") { output ->
            output.target.firstOrNull() ?: output.source
        }
    }
    
    /**
     * Use the simple GET API word by word
     */
    private suspend fun transliterateWordByWord(text: String): String {
        val words = text.trim().split("\\s+".toRegex())
        val transliteratedWords = mutableListOf<String>()
        
        for (word in words) {
            try {
                val response = api.transliterateWord(GUJARATI_CODE, word)
                if (response.success && response.result != null) {
                    val gujaratiOptions = response.result[GUJARATI_CODE]
                    if (!gujaratiOptions.isNullOrEmpty()) {
                        transliteratedWords.add(gujaratiOptions.first())
                    } else {
                        transliteratedWords.add(word)
                    }
                } else {
                    Log.w(TAG, "API returned error for word '$word': ${response.error}")
                    transliteratedWords.add(word)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to transliterate word '$word'", e)
                transliteratedWords.add(word)
            }
        }
        
        return transliteratedWords.joinToString(" ")
    }
    
    // Manual heuristic transliteration removed: we now return original text if APIs fail.

    
    /**
     * Check if text appears to be Roman Gujarati with improved detection
     */
    fun isRomanGujarati(text: String): Boolean {
        if (text.isBlank()) return false
        
        val gujaratiIndicators = listOf(
            // Common Gujarati words in Roman
            "tame", "ame", "hu", "tu", "che", "chhe", "bol", "naam",
            "maru", "taru", "tamaru", "mari", "tari", "tamari",
            "gujarati", "namaste", "dhanyawad", "shukriya",
            // Additional indicators
            "aap", "kya", "kyu", "kab", "kahan", "kaun", "kaise", "kitna",
            "chalo", "aao", "jao", "karo", "dekho", "suno", "bolo", "khao"
        )
        
        val lowercaseText = text.lowercase()
        val words = lowercaseText.split("\\s+".toRegex())
        
        // Count direct matches
        val directMatches = gujaratiIndicators.count { indicator ->
            words.any { word -> word == indicator }
        }
        
        // Also check for partial matches (prefix/suffix patterns)
        val partialMatches = gujaratiIndicators.count { indicator ->
            lowercaseText.contains(indicator)
        }
        
        // Check if text contains only Latin characters (no Devanagari or other scripts)
        val hasOnlyLatinChars = text.all { char ->
            char.isLetterOrDigit() || char.isWhitespace() || ".,!?;:'-\"()[]{}".contains(char)
        }
        
        // Decision logic
        return when {
            // Strong indicators: direct word matches
            directMatches >= 2 -> true
            directMatches >= 1 && hasOnlyLatinChars && text.any { it.isLetter() } -> true
            // Weak indicators: partial matches, need more evidence
            partialMatches >= 3 && hasOnlyLatinChars && text.any { it.isLetter() } -> true
            // Contains Gujarati script - definitely not Roman Gujarati
            text.any { it.code in 0x0A80..0x0AFF } -> false
            else -> false
        }
    }
    
    /**
     * Check if the API is available by testing with a simple word
     */
    private suspend fun isAPIAvailable(): Boolean {
        return try {
            val response = api.transliterateWord(GUJARATI_CODE, "test")
            response.success || response.error != null // API responded, even if with error
        } catch (e: Exception) {
            Log.w(TAG, "API availability check failed", e)
            false
        }
    }
    
    /**
     * Test method to verify AI4Bharat API functionality
     * Use this to test the transliteration with common Gujarati words
     */
    suspend fun testTransliteration(): Map<String, String> {
        val testWords = listOf("namaste", "tame", "ame", "hu", "dhanyawad", "gujarati")
        val results = mutableMapOf<String, String>()
        
        testWords.forEach { word ->
            try {
                val result = transliterateRomanToGujarati(word)
                results[word] = result
                Log.d(TAG, "Test: '$word' → '$result'")
            } catch (e: Exception) {
                results[word] = "ERROR: ${e.message}"
                Log.e(TAG, "Test failed for '$word'", e)
            }
        }
        
        return results
    }
}