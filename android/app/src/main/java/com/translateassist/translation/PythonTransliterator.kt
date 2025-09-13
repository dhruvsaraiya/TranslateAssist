package com.translateassist.translation

import android.content.Context
import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kotlin bridge to the embedded Python transliteration engine (Gujarati only for now).
 *
 * Usage: call [transliterate] from a coroutine (it switches to [ioDispatcher]).
 */
class PythonTransliterator(
    private val appContext: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val TAG = "PythonTransliterator"
        @Volatile private var isPythonStarted = false

        private fun ensureStarted(appContext: Context) {
            if (!isPythonStarted) {
                synchronized(this) {
                    if (!isPythonStarted) {
                        if (!Python.isStarted()) {
                            Python.start(AndroidPlatform(appContext))
                        }
                        isPythonStarted = true
                    }
                }
            }
        }
    }

    private fun getModule(): PyObject? {
        return try {
            ensureStarted(appContext)
            val py = Python.getInstance()
            py.getModule("transliteration_engine")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Python module", e)
            null
        }
    }

    suspend fun transliterate(text: String): String = withContext(ioDispatcher) {
        if (text.isBlank()) return@withContext text
        val module = getModule() ?: return@withContext text
        return@withContext try {
            val fn = module.get("transliterate")
            val result = fn.call(text)
            result.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Python transliteration failed, returning original text", e)
            text
        }
    }
}
