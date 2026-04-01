package com.albionradar.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.albionradar.AlbionRadarApp
import com.albionradar.MainActivity
import com.albionradar.R
import com.albionradar.data.EntityManager
import com.albionradar.data.RadarSettings
import com.albionradar.ui.RadarView
import kotlin.math.abs

class RadarOverlayService : android.app.Service() {

    companion object {
        const val ACTION_SHOW = "com.albionradar.action.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.albionradar.action.HIDE_OVERLAY"
        
        private const val DEFAULT_SIZE = 300
        private const val MIN_SIZE = 150
        private const val MAX_SIZE = 600
        private const val SETTINGS_PANEL_WIDTH = 400
        private const val SETTINGS_PANEL_HEIGHT = 500
    }

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var settingsView: FrameLayout? = null
    private var radarView: RadarView? = null
    private var isShowing = false
    private var currentSize = DEFAULT_SIZE
    
    private var radarParams: WindowManager.LayoutParams? = null
    private var settingsParams: WindowManager.LayoutParams? = null

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

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showOverlay() {
        if (isShowing) return

        val settings = RadarSettings.getInstance(this)
        currentSize = settings.overlaySize

        // Start foreground
        val notification = createNotification("Radar Active")
        startForeground(2, notification)

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Create radar view with transparent background
        overlayView = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        
        radarView = RadarView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        overlayView?.addView(radarView)

        // Add settings button (gear icon)
        val settingsBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_manage)
            setColorFilter(Color.WHITE)
            setBackgroundColor(0x88000000.toInt())
            setPadding(8, 8, 8, 8)
            layoutParams = FrameLayout.LayoutParams(48, 48, Gravity.TOP or Gravity.START).apply {
                marginStart = 4
                topMargin = 4
            }
            setOnClickListener {
                toggleSettingsPanel()
            }
        }
        overlayView?.addView(settingsBtn)

        // Add close button
        val closeBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.RED)
            setBackgroundColor(0x88000000.toInt())
            setPadding(8, 8, 8, 8)
            layoutParams = FrameLayout.LayoutParams(48, 48, Gravity.TOP or Gravity.END).apply {
                marginEnd = 4
                topMargin = 4
            }
            setOnClickListener {
                hideOverlay()
            }
        }
        overlayView?.addView(closeBtn)

        // Radar window params
        radarParams = WindowManager.LayoutParams(
            currentSize,
            currentSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = settings.overlayX
            y = settings.overlayY
        }

        // Add touch listener for moving
        overlayView?.setOnTouchListener(MoveTouchListener())

        try {
            windowManager?.addView(overlayView, radarParams)
            radarView?.startRendering()
            isShowing = true
        } catch (e: Exception) {
            android.util.Log.e("Overlay", "Failed to show overlay", e)
            stopSelf()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun toggleSettingsPanel() {
        if (settingsView != null && settingsView?.isAttachedToWindow == true) {
            // Hide settings
            try {
                windowManager?.removeView(settingsView)
            } catch (e: Exception) {}
            settingsView = null
            return
        }

        // Create and show settings panel
        showSettingsPanel()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showSettingsPanel() {
        val settings = RadarSettings.getInstance(this)
        
        settingsView = FrameLayout(this).apply {
            setBackgroundColor(0xEE222222.toInt())
        }

        // Create scrollable content
        val scrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(16, 16, 16, 16)
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Title
        contentLayout.addView(createTextView("RADAR SETTINGS", 16f, true, Color.YELLOW))
        contentLayout.addView(createDivider())

        // === ENTITIES SECTION ===
        contentLayout.addView(createSectionTitle("ENTITIES"))
        
        contentLayout.addView(createToggleRow("Resources", settings.showOre) { checked ->
            settings.showOre = checked
            settings.showWood = checked
            settings.showRock = checked
            settings.showFiber = checked
            settings.showHide = checked
        })
        
        contentLayout.addView(createToggleRow("Mobs", settings.showNormalMobs) { checked ->
            settings.showNormalMobs = checked
        })
        
        contentLayout.addView(createToggleRow("Bosses", settings.showBosses) { checked ->
            settings.showBosses = checked
        })
        
        contentLayout.addView(createToggleRow("Players", settings.showPlayers) { checked ->
            settings.showPlayers = checked
        })
        
        contentLayout.addView(createToggleRow("Hostile Only", settings.hostileOnly) { checked ->
            settings.hostileOnly = checked
        })
        
        contentLayout.addView(createToggleRow("Dungeons", settings.showDungeons) { checked ->
            settings.showDungeons = checked
        })
        
        contentLayout.addView(createToggleRow("Chests", settings.showChests) { checked ->
            settings.showChests = checked
        })
        
        contentLayout.addView(createToggleRow("Fishing Zones", settings.showFishing) { checked ->
            settings.showFishing = checked
        })
        
        contentLayout.addView(createToggleRow("Mist Portals", settings.showMist) { checked ->
            settings.showMist = checked
        })

        contentLayout.addView(createDivider())

        // === DISPLAY SECTION ===
        contentLayout.addView(createSectionTitle("DISPLAY"))
        
        // Size slider
        val sizeLabel = createTextView("Radar Size: ${currentSize}px", 13f, false, Color.WHITE)
        contentLayout.addView(sizeLabel)
        
        val sizeSlider = SeekBar(this).apply {
            max = MAX_SIZE - MIN_SIZE
            progress = currentSize - MIN_SIZE
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentSize = MIN_SIZE + progress
                    sizeLabel.text = "Radar Size: ${currentSize}px"
                    radarParams?.width = currentSize
                    radarParams?.height = currentSize
                    try {
                        windowManager?.updateViewLayout(overlayView, radarParams)
                    } catch (e: Exception) {}
                    settings.overlaySize = currentSize
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        contentLayout.addView(sizeSlider)

        // Min Tier slider
        val tierLabel = createTextView("Minimum Tier: T${settings.minTier}", 13f, false, Color.WHITE)
        contentLayout.addView(tierLabel)
        
        val tierSlider = SeekBar(this).apply {
            max = 7
            progress = settings.minTier - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val tier = progress + 1
                    tierLabel.text = "Minimum Tier: T$tier"
                    settings.minTier = tier
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        contentLayout.addView(tierSlider)

        contentLayout.addView(createDivider())

        // === OPTIONS SECTION ===
        contentLayout.addView(createSectionTitle("OPTIONS"))
        
        contentLayout.addView(createToggleRow("Show Grid", settings.showGrid) { checked ->
            settings.showGrid = checked
        })
        
        contentLayout.addView(createToggleRow("Show Labels", settings.showLabels) { checked ->
            settings.showLabels = checked
        })
        
        contentLayout.addView(createToggleRow("Hostile Alerts", settings.hostileAlert) { checked ->
            settings.hostileAlert = checked
        })

        scrollView.addView(contentLayout)
        settingsView?.addView(scrollView)

        // Settings window params - position next to radar
        settingsParams = WindowManager.LayoutParams(
            SETTINGS_PANEL_WIDTH,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (radarParams?.x ?: 0) + currentSize + 10
            y = radarParams?.y ?: 0
        }

        try {
            windowManager?.addView(settingsView, settingsParams)
        } catch (e: Exception) {
            android.util.Log.e("Overlay", "Failed to show settings", e)
        }
    }

    private fun createTextView(text: String, textSize: Float, bold: Boolean, color: Int): TextView {
        return TextView(this).apply {
            this.text = text
            this.textSize = textSize
            setTextColor(color)
            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }
            setPadding(0, 8, 0, 8)
        }
    }

    private fun createSectionTitle(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(Color.CYAN)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }
    }

    private fun createDivider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                topMargin = 8
                bottomMargin = 8
            }
            setBackgroundColor(0x44FFFFFF)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createToggleRow(label: String, isChecked: Boolean, onChecked: (Boolean) -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 4, 0, 4)

            addView(TextView(context).apply {
                text = label
                textSize = 13f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            addView(Switch(context).apply {
                this.isChecked = isChecked
                setOnCheckedChangeListener { _, checked -> onChecked(checked) }
            })
        }
    }

    private fun hideOverlay() {
        if (!isShowing) return

        radarView?.stopRendering()

        // Remove settings panel first
        try {
            settingsView?.let {
                if (it.isAttachedToWindow) {
                    windowManager?.removeView(it)
                }
            }
        } catch (e: Exception) {}
        settingsView = null

        // Remove radar view
        try {
            overlayView?.let {
                if (it.isAttachedToWindow) {
                    windowManager?.removeView(it)
                }
            }
        } catch (e: Exception) {}
        overlayView = null

        radarView = null
        radarParams = null
        settingsParams = null
        isShowing = false

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return androidx.core.app.NotificationCompat.Builder(this, AlbionRadarApp.CHANNEL_OVERLAY)
            .setContentTitle("Albion Radar")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private inner class MoveTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if (event == null || radarParams == null) return false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = radarParams!!.x
                    initialY = radarParams!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    radarParams!!.x = initialX + dx
                    radarParams!!.y = initialY + dy
                    windowManager?.updateViewLayout(overlayView, radarParams)
                    
                    // Also update settings panel position
                    settingsParams?.let { params ->
                        params.x = radarParams!!.x + currentSize + 10
                        params.y = radarParams!!.y
                        try {
                            windowManager?.updateViewLayout(settingsView, params)
                        } catch (e: Exception) {}
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    // Save position
                    RadarSettings.getInstance(this@RadarOverlayService).apply {
                        overlayX = radarParams!!.x
                        overlayY = radarParams!!.y
                    }
                    return true
                }
            }
            return false
        }
    }
}
