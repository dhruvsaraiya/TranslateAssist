package com.translateassist.translation

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Lightweight online translator used for TR (English/auto -> Gujarati).
 *
 * This mirrors Transliterator's network style: best-effort, no hard dependency, and returns null
 * on any network/API/parsing failure so the caller can still show transliteration or original text.
 */
class OnlineTranslator(
    private val client: OkHttpClient = defaultClient
) {
    companion object {
        private const val TAG = "OnlineTranslator"
        private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    suspend fun translateEnglishToGujarati(input: String): String? = translate(input, source = "en", target = "gu")

    suspend fun translate(input: String, source: String = "auto", target: String = "gu"): String? = withContext(Dispatchers.IO) {
        if (input.isBlank()) return@withContext null
        try {
            val url = ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("client", "gtx")
                .addQueryParameter("sl", source)
                .addQueryParameter("tl", target)
                .addQueryParameter("dt", "t")
                .addQueryParameter("q", input)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "TranslateAssist/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Online translation HTTP error: ${response.code}")
                    return@withContext null
                }
                val raw = response.body?.string() ?: return@withContext null
                parseTranslatedText(raw)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Online translation failed", e)
            null
        }
    }

    private fun parseTranslatedText(raw: String): String? {
        return try {
            val root = JSONArray(raw)
            val segments = root.optJSONArray(0) ?: return null
            val translated = StringBuilder()
            for (index in 0 until segments.length()) {
                val segment = segments.optJSONArray(index) ?: continue
                translated.append(segment.optString(0))
            }
            translated.toString().trim().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse online translation JSON", e)
            null
        }
    }
}
