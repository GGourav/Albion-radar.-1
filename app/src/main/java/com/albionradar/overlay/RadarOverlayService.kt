package com.albionradar.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.albionradar.AlbionRadarApp
import com.albionradar.MainActivity
import com.albionradar.R
import com.albionradar.data.EntityManager
import com.albionradar.data.RadarSettings
import com.albionradar.ui.RadarView

class RadarOverlayService : android.app.Service() {

    companion object {
        const val ACTION_SHOW = "com.albionradar.action.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.albionradar.action.HIDE_OVERLAY"
        private const val OVERLAY_SIZE = 300
    }

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var radarView: RadarView? = null
    private var isShowing = false
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        if (isShowing) return

        val settings = RadarSettings.getInstance(this)
        val notification = createNotification()
        startForeground(2, notification)

        layoutParams = WindowManager.LayoutParams(
            OVERLAY_SIZE,
            OVERLAY_SIZE,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 500
        }

        val root = FrameLayout(this)
        root.setBackgroundColor(0xCC000000.toInt())

        radarView = RadarView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(radarView)

        val resizeHandle = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_crop)
            layoutParams = FrameLayout.LayoutParams(48, 48).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
            setOnTouchListener(ResizeTouchListener())
        }
        root.addView(resizeHandle)

        val moveTouchListener = MoveTouchListener(layoutParams!!)
        root.setOnTouchListener(moveTouchListener)

        overlayView = root

        try {
            windowManager?.addView(overlayView, layoutParams)
            isShowing = true

            val entityManager = EntityManager.getInstance()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                radarView?.startRendering()
            }, 500)

        } catch (e: Exception) {
            android.util.Log.e("Overlay", "Failed to show overlay", e)
            stopSelf()
        }
    }

    private fun hideOverlay() {
        if (!isShowing) return
        radarView?.stopRendering()
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // View already removed
            }
        }
        overlayView = null
        radarView = null
        layoutParams = null
        isShowing = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        return AlbionRadarApp.createOverlayNotification(this, "Radar Overlay Active")
    }

    private inner class MoveTouchListener(
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var isDragging = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if (event == null) return false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager?.updateViewLayout(overlayView, params)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val intent = Intent(this@RadarOverlayService, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                    return true
                }
            }
            return false
        }
    }

    private inner class ResizeTouchListener : View.OnTouchListener {
        private var startX = 0f
        private var startY = 0f
        private var startWidth = 0
        private var startHeight = 0

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if (event == null || overlayView == null || layoutParams == null) return false

            val params = layoutParams ?: return false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startWidth = params.width
                    startHeight = params.height
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startX).toInt()
                    val dy = (event.rawY - startY).toInt()
                    val newSize = Math.max(150, Math.min(800, startWidth + dx))
                    params.width = newSize
                    params.height = newSize
                    windowManager?.updateViewLayout(overlayView, params)
                    return true
                }
            }
            return false
        }
    }
}
