package com.translateassist.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.translateassist.util.AccessibilityUtils

/**
 * Centralised handling for a tap on the floating translate dot.
 *
 * Lives outside [com.translateassist.MainActivity] so the overlay keeps working after the app is
 * swiped from recents (when no Activity is alive). It tries the live accessibility service first
 * and, if the service is enabled but not yet bound (common right after a process restart), queues
 * the extraction to run as soon as the service reconnects.
 */
object OverlayClickHandler {
    private const val TAG = "OverlayClickHandler"
    private val main = Handler(Looper.getMainLooper())

    fun onClick(context: Context) {
        val ctx = context.applicationContext

        val live = TranslateAccessibilityService.instance
        if (live != null) {
            Log.d(TAG, "Accessibility service active, extracting…")
            live.extractTextFromWhatsApp()
            return
        }

        val enabled = AccessibilityUtils.isServiceEnabled(ctx, TranslateAccessibilityService::class.java)
        if (!enabled) {
            toast(ctx, "Enable the TranslateAssist accessibility service first")
            Log.w(TAG, "Accessibility service disabled in system settings")
            return
        }

        // Enabled in settings but not currently bound (process was killed and not yet rebound).
        // Queue the extraction; it will run the moment the service reconnects.
        toast(ctx, "Waking translate service…")
        Log.d(TAG, "Queueing extraction until accessibility service reconnects")
        TranslateAccessibilityService.runWhenReady {
            main.post { TranslateAccessibilityService.instance?.extractTextFromWhatsApp() }
        }
        main.postDelayed({
            if (TranslateAccessibilityService.instance == null) {
                toast(ctx, "Service still waking up. If it doesn't respond, reopen TranslateAssist.")
            }
        }, 3000)
    }

    private fun toast(ctx: Context, msg: String) {
        main.post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    }
}
