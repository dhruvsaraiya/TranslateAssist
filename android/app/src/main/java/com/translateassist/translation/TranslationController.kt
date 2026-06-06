package com.translateassist.translation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ContextThemeWrapper
import com.translateassist.App
import com.translateassist.ui.TranslationPopup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Process-wide translation pipeline that is independent of [com.translateassist.MainActivity]'s
 * lifecycle.
 *
 * The floating overlay must keep working after the user swipes the app away from recents. Before,
 * the engine + popup were owned by MainActivity and torn down in its onDestroy(), so the overlay
 * "came up but nothing worked" once the Activity was gone. Routing everything through this
 * singleton lets the accessibility / overlay services drive translation directly using the
 * Application context, with no Activity required.
 */
object TranslationController {
    private const val TAG = "TranslationController"

    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var engine: TranslationEngine? = null

    @Volatile
    private var popup: TranslationPopup? = null

    @Synchronized
    fun init(context: Context) {
        val app = context.applicationContext
        if (engine == null) {
            engine = (app as? App)?.translationEngine ?: TranslationEngine(app)
        }
        if (popup == null) {
            // Use a themed wrapper so the popup layout's AppCompat attributes resolve correctly
            // even though we inflate from a non-Activity (Application) context.
            val themeRes = app.applicationInfo.theme
            val ctx = if (themeRes != 0) ContextThemeWrapper(app, themeRes) else app
            popup = TranslationPopup(ctx)
        }
    }

    /**
     * Translate the supplied (possibly multi-line) text and show the streaming popup. Safe to call
     * from any thread and from a Service context.
     */
    fun handleExtractedText(context: Context, text: String) {
        if (text.isBlank()) return
        init(context)
        main.post {
            val p = popup ?: return@post
            val e = engine ?: return@post
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    p.startStreaming()
                    e.translateTextStreaming(text) { pair ->
                        main.post { p.appendStreamingPair(pair) }
                    }
                    p.finalizeStreaming()
                } catch (ex: Exception) {
                    Log.e(TAG, "Translation failed", ex)
                }
            }
        }
    }
}
