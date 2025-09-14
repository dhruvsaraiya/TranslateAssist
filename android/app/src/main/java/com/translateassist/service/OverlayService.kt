package com.translateassist.service

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.translateassist.R

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var isMoving = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    companion object {
        var instance: OverlayService? = null
        var onOverlayClicked: (() -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        promoteToForeground()
        createOverlay()
    }

    private fun promoteToForeground() {
        val channelId = "translateassist_overlay"
        if (VERSION.SDK_INT >= VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr?.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(channelId, "TranslateAssist Overlay", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Keeps the floating translate button alive"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                }
                mgr?.createNotificationChannel(channel)
            }
        }
        val notification = if (VERSION.SDK_INT >= VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }.setContentTitle("TranslateAssist active")
            .setContentText("Floating translate button ready")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .build()
        try {
            startForeground(1011, notification)
        } catch (_: Exception) { /* ignore */ }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure overlay persists if process killed; system attempts restart.
        return START_STICKY
    }

    private fun createOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Create overlay view
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
    // If you add a custom drawable (e.g., shree.png) place it in res/drawable or mipmap
    // and set android:src in overlay_layout.xml. No dynamic base64 decode now.

        // Set up window parameters
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        // Add touch listener for dragging and clicking
        overlayView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isMoving = false
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isMoving = true
                        params?.x = initialX + deltaX
                        params?.y = initialY + deltaY
                        windowManager?.updateViewLayout(overlayView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving) {
                        // Button was clicked, not dragged
                        onOverlayClicked?.invoke()
                    }
                    true
                }
                else -> false
            }
        }

        // Add overlay to window manager
        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager?.removeView(it) }
        instance = null
    // Let system know we can restart if user still enabled overlay later.
    }

    override fun onBind(intent: Intent?): IBinder? = null
}