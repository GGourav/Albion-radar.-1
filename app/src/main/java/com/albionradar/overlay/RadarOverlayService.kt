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
import com.albionradar.R
import com.albionradar.data.RadarSettings
import com.albionradar.ui.RadarView
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
    private var overlayView: FrameLayout? = null
    private var radarView: RadarView? = null
    private var settingsPanel: LinearLayout? = null
    private var isShowing = false
    private var isSettingsVisible = false
    private var layoutParams: WindowManager.LayoutParams? = null
    private var currentSize = DEFAULT_SIZE
    private var sizeDisplay: TextView? = null
    private var tierDisplay: TextView? = null

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

        val root = FrameLayout(this)

        val radarContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        radarView = RadarView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        radarContainer.addView(radarView)

        val controlsContainer = createControlsPanel()
        radarContainer.addView(controlsContainer)

        root.addView(radarContainer)

        settingsPanel = createSettingsPanel()
        settingsPanel?.visibility = View.GONE
        root.addView(settingsPanel)

        root.setOnTouchListener(MoveTouchListener())

        overlayView = root

        try {
            windowManager?.addView(overlayView, layoutParams)
            isShowing = true
            radarView?.startRendering()
        } catch (e: Exception) {
            android.util.Log.e("Overlay", "Failed to show overlay", e)
            stopSelf()
        }
    }

    private fun createControlsPanel(): FrameLayout {
        return FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            val settingsBtn = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_manage)
                setBackgroundColor(Color.TRANSPARENT)
                setColorFilter(Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(40, 40).apply {
                    gravity = Gravity.TOP or Gravity.START
                    marginStart = 4
                    topMargin = 4
                }
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        toggleSettingsPanel()
                        true
                    } else false
                }
            }
            addView(settingsBtn)

            val resizeHandle = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_crop)
                setBackgroundColor(Color.TRANSPARENT)
                setColorFilter(Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(36, 36).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    marginEnd = 4
                    bottomMargin = 4
                }
                setOnTouchListener(ResizeTouchListener())
            }
            addView(resizeHandle)

            val closeBtn = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setBackgroundColor(Color.TRANSPARENT)
                setColorFilter(Color.RED)
                layoutParams = FrameLayout.LayoutParams(36, 36).apply {
                    gravity = Gravity.TOP or Gravity.END
                    marginEnd = 4
                    topMargin = 4
                }
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        hideOverlay()
                        true
                    } else false
                }
            }
            addView(closeBtn)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createSettingsPanel(): LinearLayout {
        val settings = RadarSettings.getInstance(this)
        val density = resources.displayMetrics.density

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xDD000000.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(
                (8 * density).toInt(),
                (8 * density).toInt(),
                (8 * density).toInt(),
                (8 * density).toInt()
            )

            addView(createTextView("RADAR SETTINGS", 14f, true, Color.YELLOW))
            addView(createDivider())

            addView(createTextView("Size: $currentSize px", 12f, false, Color.WHITE))
            val sizeSlider = SeekBar(context).apply {
                max = MAX_SIZE - MIN_SIZE
                progress = currentSize - MIN_SIZE
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        currentSize = MIN_SIZE + progress
                        updateSizeDisplay()
                        layoutParams?.width = currentSize
                        layoutParams?.height = currentSize
                        windowManager?.updateViewLayout(overlayView, layoutParams)
                        settings.overlaySize = currentSize
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            addView(sizeSlider)

            addView(createDivider())
            addView(createTextView("ENTITIES", 12f, true, Color.CYAN))

            addView(createToggleRow("Resources", settings.showOre) { isChecked ->
                settings.showOre = isChecked
                settings.showWood = isChecked
                settings.showRock = isChecked
                settings.showFiber = isChecked
                settings.showHide = isChecked
            })

            addView(createToggleRow("Mobs", settings.showNormalMobs) { isChecked ->
                settings.showNormalMobs = isChecked
            })

            addView(createToggleRow("Bosses", settings.showBosses) { isChecked ->
                settings.showBosses = isChecked
            })

            addView(createToggleRow("Players", settings.showPlayers) { isChecked ->
                settings.showPlayers = isChecked
            })

            addView(createToggleRow("Hostile Only", settings.hostileOnly) { isChecked ->
                settings.hostileOnly = isChecked
            })

            addView(createToggleRow("Dungeons", settings.showDungeons) { isChecked ->
                settings.showDungeons = isChecked
            })

            addView(createToggleRow("Chests", settings.showChests) { isChecked ->
                settings.showChests = isChecked
            })

            addView(createToggleRow("Fishing", settings.showFishing) { isChecked ->
                settings.showFishing = isChecked
            })

            addView(createToggleRow("Mists", settings.showMist) { isChecked ->
                settings.showMist = isChecked
            })

            addView(createDivider())

            addView(createTextView("Min Tier: ${settings.minTier}", 12f, false, Color.WHITE))
            val tierSlider = SeekBar(context).apply {
                max = 7
                progress = settings.minTier - 1
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        settings.minTier = progress + 1
                        updateTierDisplay(settings.minTier)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            addView(tierSlider)

            addView(createDivider())
            addView(createTextView("DISPLAY", 12f, true, Color.CYAN))

            addView(createToggleRow("Grid", settings.showGrid) { isChecked ->
                settings.showGrid = isChecked
            })

            addView(createToggleRow("Labels", settings.showLabels) { isChecked ->
                settings.showLabels = isChecked
            })

            addView(createToggleRow("Hostile Alert", settings.hostileAlert) { isChecked ->
                settings.hostileAlert = isChecked
            })
        }
    }

    private fun createTextView(text: String, textSize: Float, bold: Boolean, color: Int): TextView {
        return TextView(this).apply {
            this.text = text
            this.textSize = textSize
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            if (text.startsWith("Size:")) sizeDisplay = this
            if (text.startsWith("Min Tier:")) tierDisplay = this
        }
    }

    private fun createDivider(): View {
        val density = resources.displayMetrics.density
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (1 * density).toInt()
            ).apply {
                topMargin = (4 * density).toInt()
                bottomMargin = (4 * density).toInt()
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
            )

            addView(TextView(context).apply {
                text = label
                textSize = 11f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            addView(Switch(context).apply {
                this.isChecked = isChecked
                setOnCheckedChangeListener { _, checked -> onChecked(checked) }
            })
        }
    }

    private fun updateSizeDisplay() {
        sizeDisplay?.text = "Size: $currentSize px"
    }

    private fun updateTierDisplay(tier: Int) {
        tierDisplay?.text = "Min Tier: $tier"
    }

    private fun toggleSettingsPanel() {
        isSettingsVisible = !isSettingsVisible
        settingsPanel?.visibility = if (isSettingsVisible) View.VISIBLE else View.GONE
        radarView?.visibility = if (isSettingsVisible) View.GONE else View.VISIBLE
    }

    private fun hideOverlay() {
        if (!isShowing) return
        radarView?.stopRendering()
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) { }
        }
        overlayView = null
        radarView = null
        settingsPanel = null
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
        return AlbionRadarApp.createOverlayNotification(this, "Radar Active - Tap to open")
    }

    private inner class MoveTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var isDragging = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if (event == null || layoutParams == null) return false
            val params = layoutParams ?: return false

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
                    if (!isDragging && !isSettingsVisible) {
                        RadarSettings.getInstance(this@RadarOverlayService).apply {
                            overlayX = params.x
                            overlayY = params.y
                        }
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
        private var startSize = 0

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if (event == null || layoutParams == null) return false
            val params = layoutParams ?: return false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startSize = currentSize
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startX).toInt()
                    val newSize = max(MIN_SIZE, min(MAX_SIZE, startSize + dx))
                    if (newSize != currentSize) {
                        currentSize = newSize
                        params.width = currentSize
                        params.height = currentSize
                        windowManager?.updateViewLayout(overlayView, params)
                        RadarSettings.getInstance(this@RadarOverlayService).overlaySize = currentSize
                        updateSizeDisplay()
                    }
                    return true
                }
            }
            return false
        }
    }
}
