package com.sadiastudio.snakechanger

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

data class GridPoint(val x: Int, val y: Int)
data class TrailPoint(val x: Int, val y: Int, val colorHex: String)

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Direction { UP, DOWN, LEFT, RIGHT }

    private val prefs by lazy {
        context.getSharedPreferences("sgs_snake_changer_prefs", Context.MODE_PRIVATE)
    }

    private val cols = 20
    private val rows = 10

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var paused = false
    private var waiting = true
    private var isGameOver = false

    private var snake: MutableList<GridPoint> = mutableListOf()
    private var dir: Direction = Direction.RIGHT
    private var ndir: Direction = Direction.RIGHT
    private var food: GridPoint = GridPoint(14, 4)
    private var specialFood: GridPoint? = null
    private var normalFoodCount = 0
    private var score = 0
    private var levelBestScore = 0
    private var trail: MutableList<TrailPoint> = mutableListOf()

    private var curLv = 1
    private var foodIdx = 0
    private var bodyIdx = 0
    private var fullIdx = 0
    private var headIdx = 0

    private var soundOn = true
    private var vibOn = true
    private var trailOn = true

    private var touchStartX = 0f
    private var touchStartY = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    var onHudChanged: ((score: Int, bestScore: Int, speedMs: Int, level: Int) -> Unit)? = null
    var onPowerCoinEarned: ((amount: Int, coinName: String, isSpecial: Boolean) -> Unit)? = null
    var onGameOver: ((finalScore: Int, bestScore: Int, level: Int) -> Unit)? = null
    var onBoardTap: (() -> Unit)? = null

    private val gameRunnable = object : Runnable {
        override fun run() {
            if (isRunning && !paused && !waiting && !isGameOver) {
                updateStep()
                invalidate()
                if (isRunning && !paused && !waiting && !isGameOver) {
                    handler.postDelayed(this, getSpeedMs().toLong())
                }
            }
        }
    }

    init {
        loadLevelBestScore(1)
        resetBoardState(startWaiting = true)
    }

    private fun bestScoreKey(level: Int): String = "best_score_lv_${level.coerceIn(1, 15)}"

    private fun loadLevelBestScore(level: Int) {
        levelBestScore = prefs.getInt(bestScoreKey(level), 0).coerceAtLeast(0)
    }

    fun getLevelBestScore(level: Int = curLv): Int {
        return prefs.getInt(bestScoreKey(level), 0).coerceAtLeast(0)
    }

    private fun checkAndSaveLevelBest() {
        if (score > levelBestScore) {
            levelBestScore = score
            prefs.edit().putInt(bestScoreKey(curLv), levelBestScore).apply()
        }
    }

    fun configureSettings(sound: Boolean, vib: Boolean, trailEnabled: Boolean) {
        soundOn = sound
        vibOn = vib
        trailOn = trailEnabled
        if (!trailOn) {
            trail = mutableListOf()
        }
        invalidate()
    }

    fun applyTheme(level: Int, fIdx: Int, bIdx: Int, flIdx: Int, hIdx: Int) {
        curLv = level.coerceIn(1, 15)
        foodIdx = fIdx.coerceIn(0, GameData.foodItems.lastIndex)
        bodyIdx = bIdx.coerceIn(0, GameData.bodyItems.lastIndex)
        fullIdx = flIdx.coerceIn(0, GameData.fullBodies.lastIndex)
        headIdx = hIdx.coerceIn(0, GameData.heads.lastIndex)
        loadLevelBestScore(curLv)
        notifyHud()
        invalidate()
    }

    fun getCurrentScore(): Int = score
    fun getCurrentLevel(): Int = curLv

    fun getSpeedMs(): Int {
        val cfg = GameData.safeLevel(curLv)
        val base = 200
        val levelReduce = curLv * 4
        val bonus = cfg.speedBonus
        val scoreReduce = score / 3
        return max(80, base - levelReduce - bonus - scoreReduce)
    }

    fun startNewMatch(autoBegin: Boolean = false) {
        stopLoop()
        loadLevelBestScore(curLv)
        resetBoardState(startWaiting = !autoBegin)
        if (autoBegin) {
            waiting = false
            paused = false
            isGameOver = false
            isRunning = true
            playSoftTone(440, 80, ToneGenerator.TONE_PROP_BEEP)
            handler.postDelayed(gameRunnable, getSpeedMs().toLong())
        }
        notifyHud()
        invalidate()
    }

    fun steer(newDir: Direction) {
        val valid = when (newDir) {
            Direction.UP -> dir != Direction.DOWN
            Direction.DOWN -> dir != Direction.UP
            Direction.LEFT -> dir != Direction.RIGHT
            Direction.RIGHT -> dir != Direction.LEFT
        }
        if (!valid) return

        ndir = newDir
        if (isGameOver) {
            startNewMatch(autoBegin = true)
            return
        }
        if (waiting || paused) {
            resumeMatch()
        }
    }

    fun togglePauseMatch() {
        if (isGameOver) {
            startNewMatch(autoBegin = true)
            return
        }
        if (waiting || paused) {
            resumeMatch()
        } else {
            pauseMatch()
        }
    }

    fun pauseMatch() {
        if (isGameOver) return
        paused = true
        handler.removeCallbacks(gameRunnable)
        isRunning = false
        invalidate()
    }

    fun resumeMatch() {
        if (isGameOver) return
        waiting = false
        paused = false
        handler.removeCallbacks(gameRunnable)
        isRunning = true
        playSoftTone(587, 65, ToneGenerator.TONE_PROP_BEEP)
        notifyHud()
        handler.postDelayed(gameRunnable, getSpeedMs().toLong())
        invalidate()
    }

    fun stopLoop() {
        isRunning = false
        handler.removeCallbacks(gameRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopLoop()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                val threshold = 32f
                if (abs(dx) < threshold && abs(dy) < threshold) {
                    onBoardTap?.invoke()
                } else if (abs(dx) > abs(dy)) {
                    steer(if (dx > 0) Direction.RIGHT else Direction.LEFT)
                } else {
                    steer(if (dy > 0) Direction.DOWN else Direction.UP)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun resetBoardState(startWaiting: Boolean) {
        snake = mutableListOf(
            GridPoint(8, 5),
            GridPoint(7, 5),
            GridPoint(6, 5),
            GridPoint(5, 5)
        )
        trail = mutableListOf()
        dir = Direction.RIGHT
        ndir = Direction.RIGHT
        score = 0
        normalFoodCount = 0
        specialFood = null
        isGameOver = false
        waiting = startWaiting
        paused = startWaiting
        placeNormalFood()
        notifyHud()
    }

    private fun placeNormalFood() {
        var attempts = 0
        while (attempts < 150) {
            val candidate = GridPoint(Random.nextInt(1, cols - 1), Random.nextInt(1, rows - 1))
            val onSnake = snake.any { it.x == candidate.x && it.y == candidate.y }
            val onSpecial = specialFood?.let { it.x == candidate.x && it.y == candidate.y } ?: false
            if (!onSnake && !onSpecial) {
                food = candidate
                return
            }
            attempts++
        }
    }

    private fun placeSpecialFoodSafe() {
        var attempts = 0
        while (attempts < 150) {
            val candidate = GridPoint(Random.nextInt(1, cols - 1), Random.nextInt(1, rows - 1))
            val onSnake = snake.any { it.x == candidate.x && it.y == candidate.y }
            val onFood = food.x == candidate.x && food.y == candidate.y
            val onSpecial = specialFood?.let { it.x == candidate.x && it.y == candidate.y } ?: false
            if (!onSnake && !onFood && !onSpecial) {
                specialFood = candidate
                return
            }
            attempts++
        }
    }

    private fun updateStep() {
        dir = ndir
        val currentHead = snake.first()
        val nextHead = when (dir) {
            Direction.UP -> GridPoint(currentHead.x, currentHead.y - 1)
            Direction.DOWN -> GridPoint(currentHead.x, currentHead.y + 1)
            Direction.LEFT -> GridPoint(currentHead.x - 1, currentHead.y)
            Direction.RIGHT -> GridPoint(currentHead.x + 1, currentHead.y)
        }

        val lvlCfg = GameData.safeLevel(curLv)
        if (trailOn) {
            trail.add(0, TrailPoint(nextHead.x, nextHead.y, lvlCfg.trailHex))
            while (trail.size > 8) {
                trail.removeAt(trail.lastIndex)
            }
        } else {
            trail = mutableListOf()
        }

        val hitWall = nextHead.x < 0 || nextHead.x >= cols || nextHead.y < 0 || nextHead.y >= rows
        val hitSelf = snake.any { it.x == nextHead.x && it.y == nextHead.y }
        if (hitWall || hitSelf) {
            isGameOver = true
            paused = true
            waiting = true
            stopLoop()
            checkAndSaveLevelBest()
            playSoftTone(240, 200, ToneGenerator.TONE_SUP_ERROR)
            vibrate(65)
            onGameOver?.invoke(score, levelBestScore, curLv)
            notifyHud()
            return
        }

        snake.add(0, nextHead)

        val sp = specialFood
        if (sp != null && nextHead.x == sp.x && nextHead.y == sp.y) {
            score += 1
            checkAndSaveLevelBest()
            specialFood = null
            normalFoodCount = 0
            placeNormalFood()
            playSoftTone(880, 120, ToneGenerator.TONE_PROP_ACK)
            vibrate(30)
            onPowerCoinEarned?.invoke(GameData.POWER_COIN_EARN, lvlCfg.coinName, true)
        } else if (nextHead.x == food.x && nextHead.y == food.y) {
            score += 1
            checkAndSaveLevelBest()
            normalFoodCount += 1
            if (normalFoodCount % GameData.FOOD_PER_COIN == 0) {
                placeSpecialFoodSafe()
                playSoftTone(740, 105, ToneGenerator.TONE_PROP_BEEP2)
                vibrate(25)
                onPowerCoinEarned?.invoke(GameData.POWER_COIN_EARN, lvlCfg.coinName, false)
            } else {
                playSoftTone(520, 60, ToneGenerator.TONE_PROP_BEEP2)
                vibrate(12)
            }
            placeNormalFood()
        } else {
            snake.removeAt(snake.lastIndex)
        }

        notifyHud()
    }

    private fun notifyHud() {
        onHudChanged?.invoke(score, levelBestScore, getSpeedMs(), curLv)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val lvlCfg = GameData.safeLevel(curLv)
        val bgColor = parseColorSafe(lvlCfg.bgHex, Color.BLACK)
        canvas.drawColor(bgColor)

        val cellW = width.toFloat() / cols.toFloat()
        val cellH = height.toFloat() / rows.toFloat()
        val unit = min(cellW, cellH)

        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, unit * 0.04f)
        paint.color = Color.argb(24, 0, 255, 255)
        for (c in 1 until cols) {
            val x = c * cellW
            canvas.drawLine(x, 0f, x, height.toFloat(), paint)
        }
        for (r in 1 until rows) {
            val y = r * cellH
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
        }

        val borderColor = levelAccentColor(curLv, lvlCfg.trailHex)
        val borderStroke = max(3f, unit * 0.14f)
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = borderStroke
        paint.color = borderColor
        canvas.drawRect(
            borderStroke * 0.5f,
            borderStroke * 0.5f,
            width.toFloat() - borderStroke * 0.5f,
            height.toFloat() - borderStroke * 0.5f,
            paint
        )

        if (trailOn && trail.isNotEmpty()) {
            for (tp in trail) {
                val tx = tp.x * cellW + cellW * 0.5f
                val ty = tp.y * cellH + cellH * 0.5f
                paint.reset()
                paint.isAntiAlias = true
                paint.style = Paint.Style.FILL
                paint.color = parseColorSafe(tp.colorHex, Color.MAGENTA)
                paint.alpha = 105
                canvas.drawCircle(tx, ty, unit * 0.14f, paint)
            }
        }

        if (isGameOver) {
            drawOverlay(
                canvas,
                title = "GAME OVER - ${GameData.BRAND}",
                subtitle = "SCORE: $score | BEST: $levelBestScore",
                accentColor = Color.CYAN
            )
            return
        }

        if (waiting) {
            drawOverlay(
                canvas,
                title = lvlCfg.name.uppercase(),
                subtitle = "BEST: $levelBestScore | SWIPE OR ARROW TO START",
                accentColor = Color.parseColor("#00ff88")
            )
            return
        }

        if (paused) {
            drawOverlay(
                canvas,
                title = "PAUSED - ${GameData.GAME_NAME.uppercase()}",
                subtitle = "BEST: $levelBestScore | TAP OR OK TO RESUME",
                accentColor = Color.CYAN
            )
            return
        }

        val fItem = GameData.safeFood(foodIdx)
        val fx = food.x * cellW + cellW * 0.5f
        val fy = food.y * cellH + cellH * 0.5f
        drawFoodShape(canvas, fx, fy, unit * 0.36f, fItem)

        specialFood?.let { sp ->
            val sx = sp.x * cellW + cellW * 0.5f
            val sy = sp.y * cellH + cellH * 0.5f
            drawSpecialPowerCoin(canvas, sx, sy, unit * 0.44f, lvlCfg.coinName)
        }

        val effectiveFullIdx = ((lvlCfg.full + fullIdx) % GameData.fullBodies.size)
        val effectiveBodyIdx = ((curLv - 1 + bodyIdx) % GameData.bodyItems.size)
        val bItem = GameData.safeBody(effectiveBodyIdx)
        val fbItem = GameData.safeFull(effectiveFullIdx)
        val hItem = GameData.safeHead(headIdx)

        for (i in snake.indices) {
            val seg = snake[i]
            val cx = seg.x * cellW + cellW * 0.5f
            val cy = seg.y * cellH + cellH * 0.5f

            if (i == 0) {
                drawHeadCharacter(canvas, cx, cy, unit * 0.42f, hItem, lvlCfg.trailHex)
            } else if (i % 2 == 0) {
                drawCompactBodyModel(canvas, cx, cy, unit, fbItem, curLv)
            } else {
                drawBodyPattern(canvas, cx, cy, unit * 0.32f, bItem, i, curLv)
            }
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String, accentColor: Int) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(190, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        textPaint.color = accentColor
        textPaint.textSize = max(14f, min(width, height) * 0.095f)
        textPaint.isFakeBoldText = true
        canvas.drawText(title, width * 0.5f, height * 0.44f, textPaint)

        textPaint.color = Color.parseColor("#ffeb3b")
        textPaint.textSize = max(10f, min(width, height) * 0.06f)
        canvas.drawText(subtitle, width * 0.5f, height * 0.62f, textPaint)
    }

    private fun drawFoodShape(canvas: Canvas, x: Float, y: Float, r: Float, item: FoodItem) {
        val color = parseColorSafe(item.colorHex, Color.RED)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = color
        paint.style = Paint.Style.FILL

        when (item.shape) {
            "apple" -> {
                canvas.drawCircle(x, y, r, paint)
                paint.color = Color.parseColor("#00ff00")
                canvas.drawRect(x - r * 0.14f, y - r * 1.35f, x + r * 0.14f, y - r * 0.75f, paint)
            }
            "star" -> {
                val path = createStarPath(x, y, r * 1.15f, r * 0.52f, 5)
                canvas.drawPath(path, paint)
            }
            "diamond" -> {
                val path = Path().apply {
                    moveTo(x, y - r * 1.25f)
                    lineTo(x + r * 1.05f, y)
                    lineTo(x, y + r * 1.25f)
                    lineTo(x - r * 1.05f, y)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            "heart" -> {
                val path = Path().apply {
                    moveTo(x, y + r * 1.1f)
                    cubicTo(x - r * 1.4f, y + r * 0.1f, x - r * 1.1f, y - r * 1.0f, x, y - r * 0.35f)
                    cubicTo(x + r * 1.1f, y - r * 1.0f, x + r * 1.4f, y + r * 0.1f, x, y + r * 1.1f)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            "triangle" -> {
                val path = Path().apply {
                    moveTo(x, y - r * 1.1f)
                    lineTo(x + r * 1.1f, y + r * 0.95f)
                    lineTo(x - r * 1.1f, y + r * 0.95f)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            "square" -> {
                canvas.drawRect(x - r * 0.9f, y - r * 0.9f, x + r * 0.9f, y + r * 0.9f, paint)
            }
            "hexagon" -> {
                val path = Path()
                for (i in 0 until 6) {
                    val ang = i * PI / 3.0
                    val px = x + (cos(ang) * r).toFloat()
                    val py = y + (sin(ang) * r).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            "moon" -> {
                canvas.drawCircle(x, y, r, paint)
                paint.color = Color.BLACK
                canvas.drawCircle(x + r * 0.42f, y - r * 0.18f, r * 0.82f, paint)
            }
            "drop" -> {
                val path = Path().apply {
                    moveTo(x, y - r * 1.15f)
                    quadTo(x + r * 1.15f, y + r * 0.4f, x, y + r * 1.1f)
                    quadTo(x - r * 1.15f, y + r * 0.4f, x, y - r * 1.15f)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            "cross" -> {
                canvas.drawRect(x - r * 0.28f, y - r, x + r * 0.28f, y + r, paint)
                canvas.drawRect(x - r, y - r * 0.28f, x + r, y + r * 0.28f, paint)
            }
            "crown" -> {
                val path = Path().apply {
                    moveTo(x - r, y + r * 0.85f)
                    lineTo(x - r, y - r * 0.35f)
                    lineTo(x - r * 0.45f, y + r * 0.2f)
                    lineTo(x, y - r * 0.95f)
                    lineTo(x + r * 0.45f, y + r * 0.2f)
                    lineTo(x + r, y - r * 0.35f)
                    lineTo(x + r, y + r * 0.85f)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            "chilli" -> {
                val rect = RectF(x - r, y - r * 0.5f, x + r, y + r * 0.5f)
                canvas.drawOval(rect, paint)
            }
            "berry" -> {
                canvas.drawCircle(x - r * 0.38f, y, r * 0.55f, paint)
                canvas.drawCircle(x + r * 0.38f, y, r * 0.55f, paint)
            }
            "lemon" -> {
                val rect = RectF(x - r * 1.1f, y - r * 0.8f, x + r * 1.1f, y + r * 0.8f)
                canvas.drawOval(rect, paint)
            }
            "rainbow" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(2f, r * 0.35f)
                paint.color = Color.MAGENTA
                val arc = RectF(x - r, y - r, x + r, y + r)
                canvas.drawArc(arc, 180f, 180f, false, paint)
            }
            else -> canvas.drawCircle(x, y, r, paint)
        }
    }

    private fun drawSpecialPowerCoin(canvas: Canvas, x: Float, y: Float, r: Float, coinName: String) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#ffcc00")
        canvas.drawCircle(x, y, r, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.5f, r * 0.16f)
        paint.color = Color.WHITE
        canvas.drawCircle(x, y, r * 1.15f, paint)

        val shortTag = coinName.split(" ").firstOrNull()?.take(3)?.uppercase() ?: "PC"
        textPaint.color = Color.BLACK
        textPaint.textSize = max(7f, r * 0.68f)
        textPaint.isFakeBoldText = true
        canvas.drawText(shortTag, x, y + textPaint.textSize * 0.35f, textPaint)
    }

    private fun levelAccentColor(level: Int, fallbackHex: String): Int {
        val levelPalette = listOf(
            "#00f5d4", "#ffbe0b", "#fb5607", "#ff006e", "#8338ec",
            "#3a86ff", "#06d6a0", "#ffd166", "#ef476f", "#118ab2",
            "#aacc00", "#f72585", "#4cc9f0", "#f9c74f", "#90be6d"
        )
        val idx = (level - 1).coerceIn(0, levelPalette.lastIndex)
        return parseColorSafe(levelPalette[idx], parseColorSafe(fallbackHex, Color.CYAN))
    }

    private fun drawBodyPattern(
        canvas: Canvas,
        x: Float,
        y: Float,
        r: Float,
        item: BodyItem,
        idx: Int,
        level: Int
    ) {
        val baseColor = parseColorSafe(item.colorHex, Color.RED)
        val color = if (idx % 3 == 0) levelAccentColor(level, item.colorHex) else baseColor
        paint.reset()
        paint.isAntiAlias = true

        when (item.pattern) {
            "solid" -> {
                paint.style = Paint.Style.FILL
                paint.color = color
                canvas.drawCircle(x, y, r, paint)
            }
            "striped" -> {
                paint.style = Paint.Style.FILL
                paint.color = color
                canvas.drawCircle(x, y, r, paint)
                paint.color = Color.BLACK
                canvas.drawRect(x - r, y - r * 0.22f, x + r, y + r * 0.22f, paint)
            }
            "dotted" -> {
                paint.style = Paint.Style.FILL
                paint.color = color
                canvas.drawCircle(x, y, r, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(x - r * 0.25f, y - r * 0.25f, r * 0.32f, paint)
            }
            "gradient" -> {
                paint.style = Paint.Style.FILL
                paint.shader = RadialGradient(x, y, max(2f, r), Color.WHITE, color, Shader.TileMode.CLAMP)
                canvas.drawCircle(x, y, r, paint)
                paint.shader = null
            }
            "neon" -> {
                paint.style = Paint.Style.FILL
                paint.color = color
                canvas.drawCircle(x, y, r, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(1.5f, r * 0.35f)
                paint.color = Color.WHITE
                canvas.drawCircle(x, y, r * 1.1f, paint)
            }
            "checker" -> {
                paint.style = Paint.Style.FILL
                paint.color = color
                canvas.drawRect(x - r, y - r, x + r, y + r, paint)
                paint.color = Color.BLACK
                canvas.drawRect(x - r * 0.25f, y - r * 0.25f, x + r * 0.25f, y + r * 0.25f, paint)
            }
            "ring" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(1.5f, r * 0.42f)
                paint.color = color
                canvas.drawCircle(x, y, r, paint)
            }
            "wave" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(1.5f, r * 0.38f)
                paint.color = color
                val rect = RectF(x - r, y - r * 0.4f, x + r, y + r * 0.8f)
                canvas.drawArc(rect, 180f, 180f, false, paint)
            }
            "zigzag" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(1.5f, r * 0.38f)
                paint.color = color
                val p = Path().apply {
                    moveTo(x - r, y)
                    lineTo(x, y - r * 0.55f)
                    lineTo(x + r, y)
                }
                canvas.drawPath(p, paint)
            }
            "scales" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(1.2f, r * 0.3f)
                paint.color = color
                val rect = RectF(x - r, y - r, x + r, y + r)
                canvas.drawArc(rect, 0f, 180f, false, paint)
            }
            "camo" -> {
                paint.style = Paint.Style.FILL
                paint.color = color
                canvas.drawOval(RectF(x - r, y - r * 0.65f, x + r, y + r * 0.65f), paint)
            }
            "fire" -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#ff3300")
                canvas.drawCircle(x, y, r, paint)
            }
            "ice" -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#00aaff")
                canvas.drawCircle(x, y, r, paint)
            }
            "galaxy" -> {
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#5500ff")
                canvas.drawCircle(x, y, r, paint)
                paint.color = Color.parseColor("#ffffaa")
                canvas.drawCircle(x, y, r * 0.25f, paint)
            }
            "rainbow" -> {
                paint.style = Paint.Style.FILL
                paint.color = if (idx % 2 == 0) Color.MAGENTA else Color.CYAN
                canvas.drawCircle(x, y, r, paint)
            }
            else -> {
                paint.style = Paint.Style.FILL
                paint.color = color
                canvas.drawCircle(x, y, r, paint)
            }
        }
    }

    private fun drawCompactBodyModel(
        canvas: Canvas,
        x: Float,
        y: Float,
        unit: Float,
        fb: FullBodyItem,
        level: Int
    ) {
        val scale = (fb.size / 4.5f).coerceIn(0.5f, 1.15f)
        val r = unit * 0.32f * scale
        val dotColor = levelAccentColor(level, fb.dotHex)
        val centerColor = parseColorSafe(fb.centerHex, Color.WHITE)

        paint.reset()
        paint.isAntiAlias = true

        when (fb.pattern) {
            "dotMicro4" -> {
                paint.style = Paint.Style.FILL
                paint.color = centerColor
                canvas.drawCircle(x, y, r * 0.85f, paint)
                paint.color = dotColor
                for (i in 0 until 4) {
                    val ang = i * PI / 2.0
                    val dx = x + (cos(ang) * r).toFloat()
                    val dy = y + (sin(ang) * r).toFloat()
                    canvas.drawCircle(dx, dy, max(1.5f, r * 0.32f), paint)
                }
            }
            "dotRing", "ring", "ringBig" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(1.5f, r * (if (fb.pattern == "ringBig") 0.55f else 0.36f))
                paint.color = dotColor
                canvas.drawCircle(x, y, r, paint)
                paint.style = Paint.Style.FILL
                paint.color = centerColor
                canvas.drawCircle(x, y, max(1.2f, r * 0.25f), paint)
            }
            "smallStar", "starPink" -> {
                paint.style = Paint.Style.FILL
                paint.color = dotColor
                canvas.drawPath(createStarPath(x, y, r * 1.1f, r * 0.5f, 5), paint)
                paint.color = centerColor
                canvas.drawCircle(x, y, max(1.2f, r * 0.25f), paint)
            }
            "diamondStar" -> {
                paint.style = Paint.Style.FILL
                paint.color = dotColor
                val dPath = Path().apply {
                    moveTo(x, y - r * 1.2f)
                    lineTo(x + r * 1.1f, y)
                    lineTo(x, y + r * 1.2f)
                    lineTo(x - r * 1.1f, y)
                    close()
                }
                canvas.drawPath(dPath, paint)
                paint.color = centerColor
                canvas.drawCircle(x, y, max(1.2f, r * 0.28f), paint)
            }
            "finalGlow", "bigGlow", "glowDot" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = max(1.5f, r * 0.38f)
                paint.color = dotColor
                canvas.drawCircle(x, y, r * 1.05f, paint)
                paint.style = Paint.Style.FILL
                paint.color = centerColor
                canvas.drawCircle(x, y, max(1.5f, r * 0.45f), paint)
            }
            else -> {
                paint.style = Paint.Style.FILL
                paint.color = dotColor
                canvas.drawCircle(x, y, r, paint)
                paint.color = centerColor
                canvas.drawCircle(x, y, max(1.2f, r * 0.4f), paint)
            }
        }
    }

    private fun drawHeadCharacter(
        canvas: Canvas,
        x: Float,
        y: Float,
        r: Float,
        head: HeadItem,
        levelTrailHex: String
    ) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = parseColorSafe(head.colorHex, Color.YELLOW)
        canvas.drawCircle(x, y, r, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.4f, r * 0.18f)
        paint.color = parseColorSafe(levelTrailHex, Color.WHITE)
        canvas.drawCircle(x, y, r, paint)

        textPaint.color = Color.BLACK
        textPaint.isFakeBoldText = true
        textPaint.textSize = if (head.emoji.length >= 3) max(6f, r * 0.62f) else max(8f, r * 0.85f)
        canvas.drawText(head.emoji, x, y + textPaint.textSize * 0.35f, textPaint)
    }

    private fun createStarPath(cx: Float, cy: Float, outerR: Float, innerR: Float, points: Int): Path {
        val path = Path()
        val total = points * 2
        for (i in 0 until total) {
            val rad = if (i % 2 == 0) outerR else innerR
            val ang = i * PI / total - PI / 2.0
            val px = cx + (cos(ang) * rad).toFloat()
            val py = cy + (sin(ang) * rad).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return path
    }

    private fun parseColorSafe(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun playSoftTone(baseFreq: Int, durationMs: Int, fallbackToneType: Int) {
        if (!soundOn) return
        Thread {
            try {
                val sampleRate = 22050
                val numSamples = ((sampleRate * durationMs) / 1000.0).toInt().coerceAtLeast(220)
                val pcm = ShortArray(numSamples)

                val semitoneShift = 2.0.pow(((curLv - 1) % 7) / 12.0)
                val freq = (baseFreq * semitoneShift).coerceIn(180.0, 1400.0)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate.toDouble()
                    val attackEnd = numSamples * 0.18
                    val releaseStart = numSamples * 0.55
                    val envelope = when {
                        i < attackEnd -> i.toDouble() / attackEnd.coerceAtLeast(1.0)
                        i > releaseStart -> ((numSamples - i).toDouble() / (numSamples - releaseStart).coerceAtLeast(1.0)).coerceAtLeast(0.0)
                        else -> 1.0
                    }
                    val phase = 2.0 * PI * freq * t
                    val wave = sin(phase) * 0.72 + sin(phase * 2.0) * 0.14
                    pcm[i] = (wave * envelope * Short.MAX_VALUE * 0.28).toInt().toShort()
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(durationMs.toLong() + 30L)
                track.stop()
                track.release()
            } catch (_: Exception) {
                try {
                    val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 40)
                    tg.startTone(fallbackToneType, durationMs)
                    handler.postDelayed({ tg.release() }, durationMs.toLong() + 40L)
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    private fun vibrate(ms: Long) {
        if (!vibOn) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(ms)
                }
            }
        } catch (_: Exception) {
        }
    }
}
