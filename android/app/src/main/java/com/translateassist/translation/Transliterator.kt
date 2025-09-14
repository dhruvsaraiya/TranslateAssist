package com.translateassist.translation

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Transliterator that uses Google Input Tools unofficial endpoint to transliterate
 * Latin-script Gujarati phonetic input ("kem cho") into Gujarati script ("કેમ છો").
 *
 * Endpoint: https://inputtools.google.com/request?itc=gu-t-i0-und&num=1&cp=0&cs=1&ie=utf-8&oe=utf-8
 * POST body: text=<query>
 *
 * Response sample:
 * ["SUCCESS",[["kem cho",["કેમ છો"],...]]]
 */
class Transliterator(
    private val client: OkHttpClient = defaultClient
) {
    companion object {
        private const val TAG = "Transliterator"
        private const val ENDPOINT = "https://inputtools.google.com/request"
        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Attempts to transliterate the provided English phonetic Gujarati input into Gujarati script.
     * Returns null if transliteration fails or is not confident.
     */
    suspend fun transliterateToGujarati(input: String): String? = withContext(Dispatchers.IO) {
        if (input.isBlank()) return@withContext null
        try {
            val url = "$ENDPOINT?itc=gu-t-i0-und&num=1&cp=0&cs=1&ie=utf-8&oe=utf-8"
            val body = FormBody.Builder()
                .add("text", input)
                .build()
            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Transliteration HTTP error: ${'$'}{response.code}")
                    return@withContext null
                }
                val raw = response.body?.string() ?: return@withContext null
                // Response is a JSON array root
                return@withContext parseResult(raw)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Transliteration failed", e)
            null
        }
    }

    private fun parseResult(raw: String): String? {
        return try {
            val root = JSONArray(raw)
            if (root.length() < 2) return null
            val status = root.optString(0)
            if (status != "SUCCESS") return null
            val dataArray = root.optJSONArray(1) ?: return null
            if (dataArray.length() == 0) return null
            val first = dataArray.optJSONArray(0) ?: return null
            // Structure: [ original, [ candidates... ], ... ]
            val candidates = first.optJSONArray(1) ?: return null
            if (candidates.length() == 0) return null
            candidates.optString(0)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse transliteration JSON", e)
            null
        }
    }
}

/** Simple Gujarati script detection */
fun String.containsGujaratiScript(): Boolean {
    // Gujarati Unicode block: 0A80–0AFF
    return this.any { ch -> ch.code in 0x0A80..0x0AFF }
}
