package com.translateassist

import android.app.Application
import android.util.Log
import com.translateassist.translation.TranslationEngine
import kotlinx.coroutines.*

class App : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var translationEngine: TranslationEngine
        private set

    override fun onCreate() {
        super.onCreate()
        translationEngine = TranslationEngine(this)
        preWarm()
        installGlobalExceptionHandler()
    }

    private fun preWarm() {
        // Kick off model download early (non blocking)
        appScope.launch {
            try {
                translationEngine.translateText("hello") // lightweight model warm trigger
            } catch (_: Exception) {}
        }
    }

    private fun installGlobalExceptionHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e("App", "Uncaught exception in ${t.name}", e)
            prev?.uncaughtException(t, e)
        }
    }
}
