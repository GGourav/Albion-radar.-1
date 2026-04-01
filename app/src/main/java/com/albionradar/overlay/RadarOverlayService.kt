package com.albionradar.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.albionradar.AlbionRadarApp
import com.albionradar.data.RadarSettings
import com.albionradar.ui.RadarView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class RadarOverlayService : android.app.Service() {

    companion object {
        const val ACTION_SHOW = "com.albionradar.action.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.albionradar.action.HIDE_OVERLAY"
        private const val DEFAULT_SIZE = 280
        private const val MIN_SIZE = 150
        private const val MAX_SIZE = 600
    }

    private var windowManager: WindowManager? = null
    private var mainOverlayView: FrameLayout? = null
    private var radarView: RadarView? = null
    private var settingsScrollView: ScrollView? = null
    private var radarContainer: FrameLayout? = null
    private var isShowing = false
    private var isSettingsVisible = false
    private var layoutParams: WindowManager.LayoutParams? = null
    private var currentSize = DEFAULT_SIZE
    private lateinit var settings: RadarSettings

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        settings = RadarSettings.getInstance(this)
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

        currentSize = settings.overlaySize

        val notification = createNotification()
        startForeground(2, notification)

        layoutParams = WindowManager.LayoutParams(
            currentSize,
            currentSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = settings.overlayX
            y = settings.overlayY
        }

        mainOverlayView = createMainView()

        try {
            windowManager?.addView(mainOverlayView, layoutParams)
            isShowing = true
            radarView?.startRendering()
        } catch (e: Exception) {
            android.util.Log.e("Overlay", "Failed to show overlay", e)
            stopSelf()
        }
    }

    private fun createMainView(): FrameLayout {
        return FrameLayout(this).apply {
            // Root container - transparent background
            setBackgroundColor(Color.TRANSPARENT)
            
            // Radar container
            radarContainer = FrameLayout(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                addView(createRadarView())
                addView(createControlButtons())
            }
            addView(radarContainer)

            // Settings panel (initially hidden)
            settingsScrollView = ScrollView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(0xDD000000.toInt())
                isVerticalScrollBarEnabled = true
                scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                
                addView(createSettingsContent())
                visibility = View.GONE
            }
            addView(settingsScrollView)
        }
    }

    private fun createRadarView(): RadarView {
        radarView = RadarView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        return radarView!!
    }

    private fun createControlButtons(): FrameLayout {
        return FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            // Settings button (top-left)
            addView(createImageButton(android.R.drawable.ic_menu_manage, Color.WHITE) {
                toggleSettings()
            }.apply {
                layoutParams = FrameLayout.LayoutParams(dp(36), dp(36)).apply {
                    gravity = Gravity.TOP or Gravity.START
                    leftMargin = dp(4)
                    topMargin = dp(4)
                }
            })

            // Resize handle (bottom-right)
            addView(createImageButton(android.R.drawable.ic_menu_crop, Color.WHITE) {
                // Resize handled by touch
            }.apply {
                layoutParams = FrameLayout.LayoutParams(dp(32), dp(32)).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    rightMargin = dp(4)
                    bottomMargin = dp(4)
                }
                setOnTouchListener(ResizeTouchListener())
            })

            // Close button (top-right)
            addView(createImageButton(android.R.drawable.ic_menu_close_clear_cancel, Color.RED) {
                hideOverlay()
            }.apply {
                layoutParams = FrameLayout.LayoutParams(dp(32), dp(32)).apply {
                    gravity = Gravity.TOP or Gravity.END
                    rightMargin = dp(4)
                    topMargin = dp(4)
                }
            })
        }
    }

    private fun createImageButton(iconRes: Int, tintColor: Int, onClick: () -> Unit): ImageButton {
        return ImageButton(this).apply {
            setImageResource(iconRes)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(tintColor)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnClickListener { onClick() }
        }
    }

    private fun createSettingsContent(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))

            // Title
            addView(createTextView("⚙️ RADAR SETTINGS", 16f, true, Color.YELLOW))
            addView(createDivider())

            // Size section
            addView(createTextView("Overlay Size: $currentSize", 13f, false, Color.WHITE))
            addView(createSeekBar(MAX_SIZE - MIN_SIZE, currentSize - MIN_SIZE) { progress ->
                currentSize = MIN_SIZE + progress
                updateLayoutParams()
                settings.overlaySize = currentSize
            })

            addView(createDivider())
            addView(createTextView("📊 ENTITIES", 13f, true, Color.CYAN))

            // Entity toggles
            addView(createToggleRow("Resources", settings.showOre) { checked ->
                settings.showOre = checked
                settings.showWood = checked
                settings.showRock = checked
                settings.showFiber = checked
                settings.showHide = checked
            })
            addView(createToggleRow("Mobs", settings.showNormalMobs) { settings.showNormalMobs = it })
            addView(createToggleRow("Bosses", settings.showBosses) { settings.showBosses = it })
            addView(createToggleRow("Players", settings.showPlayers) { settings.showPlayers = it })
            addView(createToggleRow("Hostile Only", settings.hostileOnly) { settings.hostileOnly = it })
            addView(createToggleRow("Dungeons", settings.showDungeons) { settings.showDungeons = it })
            addView(createToggleRow("Chests", settings.showChests) { settings.showChests = it })
            addView(createToggleRow("Fishing", settings.showFishing) { settings.showFishing = it })
            addView(createToggleRow("Mists", settings.showMist) { settings.showMist = it })

            addView(createDivider())
            addView(createTextView("🎯 MIN TIER: ${settings.minTier}", 13f, false, Color.WHITE))
            addView(createSeekBar(7, settings.minTier - 1) { progress ->
                settings.minTier = progress + 1
            })

            addView(createDivider())
            addView(createTextView("🎨 DISPLAY", 13f, true, Color.CYAN))
            
            addView(createToggleRow("Show Grid", settings.showGrid) { settings.showGrid = it })
            addView(createToggleRow("Show Labels", settings.showLabels) { settings.showLabels = it })
            addView(createToggleRow("Hostile Alert", settings.hostileAlert) { settings.hostileAlert = it })

            addView(createDivider())
            addView(createTextView("ℹ️ Tap outside settings to close", 11f, false, Color.GRAY))
        }
    }

    private fun createTextView(text: String, textSize: Float, bold: Boolean, color: Int): TextView {
        return TextView(this).apply {
            this.text = text
            this.textSize = textSize
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    }

    private fun createDivider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
            setBackgroundColor(0x44FFFFFF)
        }
    }

    private fun createToggleRow(label: String, isChecked: Boolean, onChecked: (Boolean) -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }

            addView(TextView(context).apply {
                text = label
                textSize = 12f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            addView(Switch(context).apply {
                this.isChecked = isChecked
                setOnCheckedChangeListener { _, checked -> onChecked(checked) }
            })
        }
    }

    private fun createSeekBar(max: Int, progress: Int, onChange: (Int) -> Unit): SeekBar {
        return SeekBar(this).apply {
            this.max = max
            this.progress = progress
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) onChange(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun updateLayoutParams() {
        layoutParams?.width = currentSize
        layoutParams?.height = currentSize
        windowManager?.updateViewLayout(mainOverlayView, layoutParams)
    }

    private fun toggleSettings() {
        isSettingsVisible = !isSettingsVisible
        settingsScrollView?.visibility = if (isSettingsVisible) View.VISIBLE else View.GONE
        radarContainer?.visibility = if (isSettingsVisible) View.GONE else View.VISIBLE
        
        // Update text with current size
        if (isSettingsVisible) {
            // Refresh settings panel if needed
        }
    }

    private fun hideOverlay() {
        if (!isShowing) return
        radarView?.stopRendering()
        mainOverlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) { }
        }
        mainOverlayView = null
        radarView = null
        settingsScrollView = null
        radarContainer = null
        layoutParams = null
        isShowing = false
        isSettingsVisible = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        return AlbionRadarApp.createOverlayNotification(this, "Radar Active")
    }

    private inner class ResizeTouchListener : View.OnTouchListener {
        private var startX = 0f
        private var startY = 0f
        private var startSize = 0

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if (event == null || layoutParams == null) return false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startSize = currentSize
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startX).toInt()
                    val dy = (event.rawY - startY).toInt()
                    val delta = if (abs(dx) > abs(dy)) dx else dy
                    val newSize = max(MIN_SIZE, min(MAX_SIZE, startSize + delta))
                    
                    if (newSize != currentSize) {
                        currentSize = newSize
                        updateLayoutParams()
                        settings.overlaySize = currentSize
                    }
                    return true
                }
            }
            return false
        }
    }
}
