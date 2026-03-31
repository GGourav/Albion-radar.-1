package com.albionradar.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import com.albionradar.data.*
import com.albionradar.R
import kotlinx.coroutines.*
import kotlin.math.*

class RadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val INTERPOLATION_FACTOR = 0.15f
        private const val DEFAULT_RANGE = 80f
        private const val PLAYER_DOT_RADIUS = 6f
        private const val ENTITY_BASE_RADIUS = 4f
    }

    private val entityManager = EntityManager.getInstance()
    private val settings = RadarSettings.getInstance(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint()
    private val entityPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val healthBarPaint = Paint()
    private val healthBarBgPaint = Paint()
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var isRendering = false
    private var choreographer: Choreographer? = null
    private var renderCallback: Choreographer.FrameCallback? = null
    private var renderScope: CoroutineScope? = null

    private var centerX = 0f
    private var centerY = 0f
    private var radarRange = DEFAULT_RANGE
    private var lastFrameTime = 0L

    init {
        paint.color = Color.WHITE
        paint.textSize = 24f
        paint.textAlign = Paint.Align.CENTER

        textPaint.color = Color.WHITE
        textPaint.textSize = 18f
        textPaint.textAlign = Paint.Align.CENTER

        gridPaint.color = Color.parseColor("#33FFFFFF")
        gridPaint.style = Paint.Style.STROKE
        gridPaint.strokeWidth = 1f

        entityPaint.style = Paint.Style.FILL
        entityPaint.isAntiAlias = true

        healthBarPaint.style = Paint.Style.FILL
        healthBarBgPaint.color = Color.parseColor("#44000000")
        healthBarBgPaint.style = Paint.Style.FILL

        centerDotPaint.color = Color.BLUE
        centerDotPaint.style = Paint.Style.FILL
    }

    fun startRendering() {
        if (isRendering) return
        isRendering = true
        renderScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        choreographer = Choreographer.getInstance()
        renderCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!isRendering) return
                val dt = if (lastFrameTime > 0) (frameTimeNanos - lastFrameTime) / 1_000_000f else 16f
                lastFrameTime = frameTimeNanos
                updateInterpolation(dt)
                invalidate()
                choreographer?.postFrameCallback(this)
            }
        }
        choreographer?.postFrameCallback(renderCallback!!)
    }

    fun stopRendering() {
        isRendering = false
        renderCallback?.let { choreographer?.removeFrameCallback(it) }
        renderScope?.cancel()
        renderScope = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val currentZoom = settings.zoomLevel
        radarRange = DEFAULT_RANGE / currentZoom

        drawBackground(canvas, w, h)
        if (settings.showGrid) drawGrid(canvas, w, h)
        drawEntities(canvas, w, h)
        drawCenterPlayer(canvas)
        drawCompass(canvas, w, h)
    }

    private fun drawBackground(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(Color.parseColor("#0D0D0D"))
        paint.color = Color.parseColor("#1A3A1A")
        paint.style = Paint.Style.FILL
        val rangeRadius = Math.min(w, h) / 2f
        canvas.drawCircle(centerX, centerY, rangeRadius, paint)
    }

    private fun drawGrid(canvas: Canvas, w: Float, h: Float) {
        val rangeRadius = Math.min(w, h) / 2f
        val gridSpacing = radarRange / 4f
        val pixelsPerUnit = rangeRadius / (radarRange / 2f)

        canvas.save()
        canvas.clipRect(0f, 0f, w, h)

        for (i in 1..4) {
            val gridRadius = i * gridSpacing * pixelsPerUnit
            canvas.drawCircle(centerX, centerY, gridRadius, gridPaint)
        }

        canvas.drawLine(centerX, 0f, centerX, h, gridPaint)
        canvas.drawLine(0f, centerY, w, centerY, gridPaint)
        canvas.restore()
    }

    private fun drawEntities(canvas: Canvas, w: Float, h: Float) {
        val entities = entityManager.entities.value
        val rangeRadius = Math.min(w, h) / 2f
        val pixelsPerUnit = rangeRadius / (radarRange / 2f)

        for (entity in entities) {
            if (!shouldDrawEntity(entity)) continue

            val dx = entity.displayX - 0f
            val dy = entity.displayY - 0f
            val distance = sqrt(dx * dx + dy * dy)

            if (distance > radarRange / 2f) continue

            val screenX = centerX + dx * pixelsPerUnit
            val screenY = centerY + dy * pixelsPerUnit

            drawEntity(canvas, entity, screenX, screenY)
        }
    }

    private fun drawEntity(canvas: Canvas, entity: GameEntity, x: Float, y: Float) {
        when (entity.type) {
            EntityType.RESOURCE -> drawResource(canvas, entity, x, y)
            EntityType.MOB -> drawMob(canvas, entity, x, y)
            EntityType.PLAYER -> drawPlayer(canvas, entity, x, y)
            EntityType.DUNGEON -> drawDungeon(canvas, entity, x, y)
            EntityType.CHEST -> drawChest(canvas, entity, x, y)
            EntityType.FISHING -> drawFishing(canvas, entity, x, y)
            EntityType.MIST, EntityType.MIST_PORTAL -> drawMist(canvas, entity, x, y)
            EntityType.WISP_CAGE -> drawWispCage(canvas, entity, x, y)
        }
    }

    private fun drawResource(canvas: Canvas, entity: GameEntity, x: Float, y: Float) {
        val color = getResourceColor(entity.resourceType, entity.tier)
        val radius = ENTITY_BASE_RADIUS + entity.enchant * 0.5f

        entityPaint.color = color
        canvas.drawCircle(x, y, radius, entityPaint)

        if (entity.size > 0) {
            drawChargeIndicator(canvas, x, y + radius + 4f, entity.size, color)
        }

        if (settings.showLabels) {
            textPaint.color = color
            textPaint.textSize = 14f
            val label = "${entity.tier}${if (entity.enchant > 0) ".${entity.enchant}" else ""}"
            canvas.drawText(label, x, y - radius - 4f, textPaint)
        }
    }

    private fun drawMob(canvas: Canvas, entity: GameEntity, x: Float, y: Float) {
        val color = getMobColor(entity.enemyType)
        val radius = when {
            entity.enemyType == 6 -> ENTITY_BASE_RADIUS + 3f
            entity.enemyType == 5 -> ENTITY_BASE_RADIUS + 2f
            entity.enemyType == 4 -> ENTITY_BASE_RADIUS + 1f
            else -> ENTITY_BASE_RADIUS
        }

        entityPaint.color = color
        canvas.drawCircle(x, y, radius, entityPaint)

        if (entity.health > 0 && entity.maxHealth > 0 && entity.enemyType > 1) {
            drawHealthBar(canvas, x, y - radius - 6f, entity.health, entity.maxHealth, 20f, 3f)
        }

        if (settings.showLabels && entity.enemyType >= 4) {
            textPaint.color = color
            textPaint.textSize = 12f
            canvas.drawText(entity.displayName, x, y - radius - 10f, textPaint)
        }
    }

    private fun drawPlayer(canvas: Canvas, entity: GameEntity, x: Float, y: Float) {
        if (!settings.shouldShowPlayer(entity.faction)) return

        val color = getPlayerColor(entity.faction)
        val radius = if (entity.mounted) ENTITY_BASE_RADIUS + 2f else ENTITY_BASE_RADIUS

        entityPaint.color = color
        canvas.drawCircle(x, y, radius, entityPaint)

        if (entity.isHostile && settings.hostileAlert) {
            paint.style = Paint.Style.STROKE
            paint.color = Color.RED
            paint.strokeWidth = 2f
            canvas.drawCircle(x, y, radius + 4f, paint)
            paint.style = Paint.Style.FILL
        }

        if (settings.showLabels) {
            textPaint.color = color
            textPaint.textSize = 14f
            canvas.drawText(entity.name, x, y - radius - 4f, textPaint)
        }
    }

    private fun drawDungeon(canvas: Canvas, entity: GameEntity, x: Float, y: Float) {
        val color = when (entity.dungeonType) {
            0 -> Color.parseColor("#00BCD4")
            1 -> Color.parseColor("#2196F3")
            2 -> Color.parseColor("#FF5722")
            3 -> Color.parseColor("#9C27B0")
            else -> Color.CYAN
        }

        entityPaint.color = color
        canvas.drawCircle(x, y, 6f, entityPaint)

        if (settings.showLabels) {
            textPaint.color = color
            textPaint.textSize = 14f
            val label = when (entity.dungeonType) {
                0 -> "Solo ${entity.enchant}"
                1 -> "Group ${entity.enchant}"
                2 -> "Corrupted"
                3 -> "Hellgate"
                else -> "Dungeon"
            }
            canvas.drawText(label, x, y - 10f, textPaint)
        }
    }

    private fun drawChest(canvas: Canvas, entity: GameEntity, x: Float, y: Float) {
        val color = when (entity.chestRarity) {
            "legendary" -> Color.parseColor("#FFD700")
            "rare" -> Color.parseColor("#9C27B0")
            "blue" -> Color.parseColor("#2196F3")
            else -> Color.parseColor("#4CAF50")
        }

        entityPaint.color = color
        val rect = RectF(x - 5f, y - 4f, x + 5f, y + 4f)
        canvas.drawRoundRect(rect, 2f, 2f, entityPaint)

        if (settings.showLabels) {
            textPaint.color = color
            textPaint.textSize = 12f
            canvas.drawText(entity.chestRarity.ifEmpty { "Chest" }, x, y - 8f, textPaint)
        }
    }

    private fun drawFishing(canvas: Canvas, entity: GameEntity, x: Float, y: Float) {
        entityPaint.color = Color.parseColor("#2196F3")
        canvas.drawCircle(x, y, 4f, entityPaint)

        if (settings.showLabels) {
            textPaint.color = Color.parseColor("#2196F3")
            textPaint.textSize = 12f
            canvas.drawText("Fish ${entity.fishSize}/${entity.fishTotal}", x, y - 8f, textPaint)
        }
    }

    private fun drawMist(canvas: Canvas, entity: GameEntity, x: Float, y: Float) {
        entityPaint.color = Color.parseColor("#9C27B0")
        canvas.drawCircle(x, y, 5f, entityPaint)

        if (settings.showLabels) {
            textPaint.color = Color.parseColor("#9C27B0")
            textPaint.textSize = 14f
            canvas.drawText("Mist .${entity.enchant}", x, y - 8f, textPaint)
        }
    }

    private fun drawWispCage(canvas: Canvas, entity: GameEntity, x: Float, y: Float) {
        entityPaint.color = Color.parseColor("#FFD700")
        canvas.drawCircle(x, y, 5f, entityPaint)

        if (settings.showLabels) {
            textPaint.color = Color.parseColor("#FFD700")
            textPaint.textSize = 12f
            canvas.drawText("Wisp", x, y - 8f, textPaint)
        }
    }

    private fun drawCenterPlayer(canvas: Canvas) {
        centerDotPaint.color = Color.BLUE
        canvas.drawCircle(centerX, centerY, PLAYER_DOT_RADIUS, centerDotPaint)
        centerDotPaint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, PLAYER_DOT_RADIUS - 2f, centerDotPaint)
        centerDotPaint.color = Color.BLUE
        canvas.drawCircle(centerX, centerY, 2f, centerDotPaint)
    }

    private fun drawCompass(canvas: Canvas, w: Float, h: Float) {
        textPaint.color = Color.parseColor("#88FFFFFF")
        textPaint.textSize = 16f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("N", centerX, 20f, textPaint)
        canvas.drawText("S", centerX, h - 8f, textPaint)
        canvas.drawText("W", 20f, centerY, textPaint)
        canvas.drawText("E", w - 20f, centerY, textPaint)
    }

    private fun drawHealthBar(canvas: Canvas, x: Float, y: Float, current: Int, max: Int, width: Float, height: Float) {
        val hpPercent = if (max > 0) current.toFloat() / max.toFloat() else 0f
        val left = x - width / 2f
        val top = y - height / 2f

        healthBarBgPaint.color = Color.parseColor("#44000000")
        canvas.drawRoundRect(RectF(left, top, left + width, top + height), 1f, 1f, healthBarBgPaint)

        val hpColor = when {
            hpPercent > 0.6f -> Color.GREEN
            hpPercent > 0.3f -> Color.YELLOW
            else -> Color.RED
        }
        healthBarPaint.color = hpColor
        canvas.drawRoundRect(RectF(left, top, left + width * hpPercent, top + height), 1f, 1f, healthBarPaint)
    }

    private fun drawChargeIndicator(canvas: Canvas, x: Float, y: Float, charges: Int, color: Int) {
        val dotSize = 2f
        val spacing = 6f
        val totalWidth = charges * spacing
        val startX = x - totalWidth / 2f

        entityPaint.color = color
        for (i in 0 until charges) {
            canvas.drawCircle(startX + i * spacing, y, dotSize, entityPaint)
        }
    }

    private fun updateInterpolation(dt: Float) {
        val entities = entityManager.entities.value
        val factor = INTERPOLATION_FACTOR.coerceIn(0.05f, 0.5f)

        for (entity in entities) {
            val dx = entity.posX - entity.displayX
            val dy = entity.posY - entity.displayY

            if (abs(dx) > 0.01f || abs(dy) > 0.01f) {
                val newDisplayX = entity.displayX + dx * factor
                val newDisplayY = entity.displayY + dy * factor
                entityManager.addOrUpdateEntity(entity.copy(displayX = newDisplayX, displayY = newDisplayY))
            }
        }
    }

    private fun shouldDrawEntity(entity: GameEntity): Boolean {
        return when (entity.type) {
            EntityType.RESOURCE -> entity.tier >= settings.minTier && settings.shouldShowResource(entity.resourceType)
            EntityType.MOB -> settings.shouldShowMob(entity.enemyType)
            EntityType.PLAYER -> settings.shouldShowPlayer(entity.faction)
            EntityType.DUNGEON -> settings.showDungeons
            EntityType.CHEST -> settings.showChests
            EntityType.FISHING -> settings.showFishing
            EntityType.MIST, EntityType.MIST_PORTAL -> settings.showMist
            EntityType.WISP_CAGE -> settings.showMist
        }
    }

    private fun getResourceColor(type: String, tier: Int): Int {
        return when (type.uppercase()) {
            "ORE" -> Color.parseColor("#FFD700")
            "WOOD", "LOG", "LOGS" -> Color.parseColor("#8B4513")
            "ROCK" -> Color.parseColor("#808080")
            "FIBER" -> Color.parseColor("#9370DB")
            "HIDE" -> Color.parseColor("#CD853F")
            else -> getTierColor(tier)
        }
    }

    private fun getMobColor(enemyType: Int): Int {
        return when (enemyType) {
            0, 1 -> Color.parseColor("#FFD700")
            2 -> Color.parseColor("#9E9E9E")
            3 -> Color.parseColor("#FFFF00")
            4 -> Color.parseColor("#9370DB")
            5 -> Color.parseColor("#FF8C00")
            6 -> Color.parseColor("#FF0000")
            7 -> Color.parseColor("#00FFFF")
            8 -> Color.parseColor("#FF1493")
            9 -> Color.WHITE
            else -> Color.parseColor("#4169E1")
        }
    }

    private fun getPlayerColor(faction: Int): Int {
        return when (faction) {
            0 -> Color.parseColor("#2196F3")
            1 -> Color.parseColor("#FF9800")
            2 -> Color.parseColor("#FF9800")
            3 -> Color.parseColor("#FF9800")
            4 -> Color.parseColor("#FF9800")
            5 -> Color.parseColor("#FF9800")
            6 -> Color.parseColor("#FF9800")
            255 -> Color.parseColor("#F44336")
            else -> Color.parseColor("#2196F3")
        }
    }

    private fun getTierColor(tier: Int): Int {
        return when (tier) {
            1 -> Color.parseColor("#808080")
            2 -> Color.parseColor("#4CAF50")
            3 -> Color.parseColor("#2196F3")
            4 -> Color.parseColor("#9C27B0")
            5 -> Color.parseColor("#FF9800")
            6 -> Color.parseColor("#607D8B")
            7 -> Color.parseColor("#00BCD4")
            8 -> Color.parseColor("#E91E63")
            else -> Color.WHITE
        }
    }
}
