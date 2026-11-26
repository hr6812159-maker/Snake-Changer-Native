package com.sadiastudio.snakechanger

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Direction { UP, DOWN, LEFT, RIGHT }

    data class HudSnapshot(
        val coins: Int,
        val score: Int,
        val bestScore: Int,
        val level: Int,
        val speedMs: Long,
        val paused: Boolean,
        val gameOver: Boolean,
        val waiting: Boolean
    )

    var onHudUpdate: ((HudSnapshot) -> Unit)? = null

    private val prefs = context.getSharedPreferences("sgs_native_prefs", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    private val cols = 20
    private val rows = 10

    private val snake = ArrayList<Point>()
    private val trail = ArrayList<Pair<Point, Int>>()
    private var dir = Direction.RIGHT
    private var nextDir = Direction.RIGHT
    private var food = Point(14, 4)
    private var specialFood: Point? = null
    private var normalFoodCount = 0

    private var score = 0
    private var coins = GameData.START_COINS
    private var currentLevel = 1

    private var paused = true
    private var waiting = true
    private var isGameOver = false

    private var soundOn = true
    private var vibOn = true
    private var trailOn = true

    private var touchStartX = 0f
    private var touchStartY = 0f

    private var toneGen: ToneGenerator? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!paused && !waiting && !isGameOver) {
                stepGame()
                handler.postDelayed(this, getSpeedMs())
            }
        }
    }

    init {
        loadSavedState()
        initTone()
        resetBoardForLevel(currentLevel, keepWaiting = true)
    }

    private fun initTone() {
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 35)
        } catch (_: Exception) {
            toneGen = null
        }
    }

    private fun playSoftTone(durationMs: Int = 65) {
        if (!soundOn) return
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, durationMs.coerceIn(30, 140))
        } catch (_: Exception) {
        }
    }

    private fun vibrateSoft(ms: Long = 25L) {
        if (!vibOn) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vib?.vibrate(ms)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun loadSavedState() {
        soundOn = prefs.getBoolean("pref_sound", true)
        vibOn = prefs.getBoolean("pref_vib", true)
        trailOn = prefs.getBoolean("pref_trail", true)

        val savedLv = prefs.getInt("cur_lv", 1)
        currentLevel = savedLv.coerceIn(1, 15)

        val encodedCoins = prefs.getString("sgs_secure_coins", null)
        coins = if (encodedCoins == null) {
            val startVal = GameData.START_COINS
            prefs.edit().putString("sgs_secure_coins", GameData.encodeCoins(startVal)).apply()
            startVal
        } else {
            GameData.decodeCoins(encodedCoins)
        }
    }

    fun getCoins(): Int = coins

    fun setCoins(newCoins: Int) {
        coins = max(0, newCoins)
        prefs.edit().putString("sgs_secure_coins", GameData.encodeCoins(coins)).apply()
        notifyHud()
    }

    fun addCoins(delta: Int) {
        setCoins(coins + delta)
    }

    fun getCurrentLevel(): Int = currentLevel

    fun getLevelBestScore(lv: Int = currentLevel): Int {
        val safeLv = lv.coerceIn(1, 15)
        return max(0, prefs.getInt("sgs_best_lv_$safeLv", 0))
    }

    private fun updateLevelBestScore(lv: Int, newScore: Int): Int {
        val safeLv = lv.coerceIn(1, 15)
        val best = max(getLevelBestScore(safeLv), max(0, newScore))
        prefs.edit().putInt("sgs_best_lv_$safeLv", best).apply()
        return best
    }

    fun resetCurrentLevelBestScore() {
        val safeLv = currentLevel.coerceIn(1, 15)
        prefs.edit().putInt("sgs_best_lv_$safeLv", 0).apply()
        notifyHud()
        invalidate()
    }

    fun selectLevel(lv: Int) {
        val safeLv = lv.coerceIn(1, 15)
        currentLevel = safeLv
        prefs.edit().putInt("cur_lv", currentLevel).apply()
        resetBoardForLevel(currentLevel, keepWaiting = true)
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundOn = enabled
        prefs.edit().putBoolean("pref_sound", soundOn).apply()
        if (soundOn) playSoftTone(60)
    }

    fun isSoundEnabled(): Boolean = soundOn

    fun setVibrationEnabled(enabled: Boolean) {
        vibOn = enabled
        prefs.edit().putBoolean("pref_vib", vibOn).apply()
        if (vibOn) vibrateSoft(20L)
    }

    fun isVibrationEnabled(): Boolean = vibOn

    fun setTrailEnabled(enabled: Boolean) {
        trailOn = enabled
        prefs.edit().putBoolean("pref_trail", trailOn).apply()
        if (!trailOn) trail.clear()
        invalidate()
    }

    fun isTrailEnabled(): Boolean = trailOn

    fun getSpeedMs(): Long {
        val cfg = GameData.safeLevel(currentLevel)
        val base = 200
        val levelReduce = currentLevel * 4
        val scoreReduce = score / 3
        val speed = max(80, base - levelReduce - cfg.speedBonus - scoreReduce)
        return speed.toLong()
    }

    fun startNewMatch() {
        resetBoardForLevel(currentLevel, keepWaiting = false)
        paused = false
        waiting = false
        isGameOver = false
        playSoftTone(75)
        scheduleTick()
        notifyHud()
        invalidate()
    }

    fun togglePause() {
        if (isGameOver) {
            startNewMatch()
            return
        }
        if (waiting) {
            waiting = false
            paused = false
            playSoftTone(70)
            scheduleTick()
            notifyHud()
            invalidate()
            return
        }
        paused = !paused
        if (paused) {
            handler.removeCallbacks(tickRunnable)
        } else {
            scheduleTick()
        }
        notifyHud()
        invalidate()
    }

    fun pauseForOverlay() {
        if (!paused) {
            paused = true
            handler.removeCallbacks(tickRunnable)
            notifyHud()
            invalidate()
        }
    }

    fun setDirection(d: Direction) {
        val validTurn = when (d) {
            Direction.UP -> dir != Direction.DOWN
            Direction.DOWN -> dir != Direction.UP
            Direction.LEFT -> dir != Direction.RIGHT
            Direction.RIGHT -> dir != Direction.LEFT
        }
        if (!validTurn) return
        nextDir = d
        if (isGameOver || waiting || paused) {
            startNewMatch()
        }
    }

    private fun resetBoardForLevel(lv: Int, keepWaiting: Boolean) {
        handler.removeCallbacks(tickRunnable)
        currentLevel = lv.coerceIn(1, 15)
        snake.clear()
        snake.add(Point(8, 5))
        snake.add(Point(7, 5))
        snake.add(Point(6, 5))
        snake.add(Point(5, 5))
        trail.clear()
        dir = Direction.RIGHT
        nextDir = Direction.RIGHT
        score = 0
        normalFoodCount = 0
        specialFood = null
        isGameOver = false
        waiting = keepWaiting
        paused = keepWaiting
        placeNormalFood()
        notifyHud()
        invalidate()
    }

    private fun scheduleTick() {
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, getSpeedMs())
    }

    private fun placeNormalFood() {
        var attempts = 0
        while (attempts < 150) {
            val fx = Random.nextInt(1, cols - 1)
            val fy = Random.nextInt(1, rows - 1)
            val onSnake = snake.any { it.x == fx && it.y == fy }
            val onSpecial = specialFood?.let { it.x == fx && it.y == fy } == true
            if (!onSnake && !onSpecial) {
                food = Point(fx, fy)
                return
            }
            attempts++
        }
        food = Point(cols / 2, rows / 2)
    }

    private fun placeSpecialFood() {
        var attempts = 0
        while (attempts < 150) {
            val sx = Random.nextInt(1, cols - 1)
            val sy = Random.nextInt(1, rows - 1)
            val onSnake = snake.any { it.x == sx && it.y == sy }
            val onFood = food.x == sx && food.y == sy
            if (!onSnake && !onFood) {
                specialFood = Point(sx, sy)
                return
            }
            attempts++
        }
    }

    private fun stepGame() {
        dir = nextDir
        val head = Point(snake.first().x, snake.first().y)
        when (dir) {
            Direction.UP -> head.y -= 1
            Direction.DOWN -> head.y += 1
            Direction.LEFT -> head.x -= 1
            Direction.RIGHT -> head.x += 1
        }

        val cfg = GameData.safeLevel(currentLevel)
        if (trailOn) {
            trail.add(0, Pair(Point(head.x, head.y), Color.parseColor(cfg.trailHex)))
            if (trail.size > 8) {
                trail.removeAt(trail.lastIndex)
            }
        } else {
            trail.clear()
        }

        val hitWall = head.x < 0 || head.x >= cols || head.y < 0 || head.y >= rows
        val hitSelf = snake.any { it.x == head.x && it.y == head.y }
        if (hitWall || hitSelf) {
            handler.removeCallbacks(tickRunnable)
            isGameOver = true
            paused = true
            waiting = true
            updateLevelBestScore(currentLevel, score)
            playSoftTone(110)
            vibrateSoft(45L)
            notifyHud()
            invalidate()
            return
        }

        snake.add(0, head)

        val sp = specialFood
        if (sp != null && head.x == sp.x && head.y == sp.y) {
            score += 1
            updateLevelBestScore(currentLevel, score)
            addCoins(GameData.SPECIAL_COIN_REWARD)
            specialFood = null
            playSoftTone(90)
            vibrateSoft(30L)
            placeNormalFood()
        } else if (head.x == food.x && head.y == food.y) {
            score += 1
            updateLevelBestScore(currentLevel, score)
            normalFoodCount += 1
            if (normalFoodCount % GameData.FOODS_PER_POWER_COIN == 0) {
                addCoins(GameData.POWER_COINS_PER_10_FOOD)
                playSoftTone(85)
                placeSpecialFood()
            } else {
                playSoftTone(55)
            }
            placeNormalFood()
        } else {
            snake.removeAt(snake.lastIndex)
        }

        notifyHud()
        invalidate()
    }

    private fun notifyHud() {
        onHudUpdate?.invoke(
            HudSnapshot(
                coins = coins,
                score = score,
                bestScore = getLevelBestScore(currentLevel),
                level = currentLevel,
                speedMs = getSpeedMs(),
                paused = paused,
                gameOver = isGameOver,
                waiting = waiting
            )
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                if (abs(dx) > 36f || abs(dy) > 36f) {
                    if (abs(dx) > abs(dy)) {
                        setDirection(if (dx > 0) Direction.RIGHT else Direction.LEFT)
                    } else {
                        setDirection(if (dy > 0) Direction.DOWN else Direction.UP)
                    }
                } else if (waiting || paused || isGameOver) {
                    startNewMatch()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cfg = GameData.safeLevel(currentLevel)
        bgPaint.color = Color.parseColor(cfg.bgHex)
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val cellW = w / cols.toFloat()
        val cellH = h / rows.toFloat()
        val unit = min(cellW, cellH)

        if (trailOn) {
            for (item in trail) {
                val cx = (item.first.x + 0.5f) * cellW
                val cy = (item.first.y + 0.5f) * cellH
                shapePaint.color = item.second
                canvas.drawCircle(cx, cy, unit * 0.14f, shapePaint)
            }
        }

        val foodCfg = GameData.foodItems.getOrElse(cfg.food) { GameData.foodItems.first() }
        val fx = (food.x + 0.5f) * cellW
        val fy = (food.y + 0.5f) * cellH
        drawFood(canvas, fx, fy, unit * 0.34f, foodCfg)

        specialFood?.let { sp ->
            val sx = (sp.x + 0.5f) * cellW
            val sy = (sp.y + 0.5f) * cellH
            drawPowerCoin(canvas, sx, sy, unit * 0.40f)
        }

        val headCfg = GameData.heads.getOrElse(cfg.head) { GameData.heads.first() }
        val bodyCfg = GameData.bodyItems.getOrElse(cfg.body) { GameData.bodyItems.first() }
        val fullCfg = GameData.fullBodies.getOrElse(cfg.full) { GameData.fullBodies.first() }

        snake.forEachIndexed { idx, seg ->
            val cx = (seg.x + 0.5f) * cellW
            val cy = (seg.y + 0.5f) * cellH
            if (idx == 0) {
                shapePaint.color = Color.parseColor(headCfg.colorHex)
                canvas.drawCircle(cx, cy, unit * 0.42f, shapePaint)
                textPaint.color = Color.BLACK
                textPaint.textSize = unit * 0.36f
                canvas.drawText(headCfg.emoji, cx, cy + unit * 0.12f, textPaint)
            } else if (idx % 2 == 0) {
                shapePaint.color = Color.parseColor(fullCfg.colorHex)
                canvas.drawCircle(cx, cy, unit * 0.34f, shapePaint)
                shapePaint.color = Color.parseColor(fullCfg.centerHex)
                canvas.drawCircle(cx, cy, unit * 0.14f, shapePaint)
            } else {
                shapePaint.color = Color.parseColor(bodyCfg.colorHex)
                canvas.drawCircle(cx, cy, unit * 0.32f, shapePaint)
            }
        }

        if (isGameOver) {
            bgPaint.color = Color.argb(205, 0, 0, 0)
            canvas.drawRect(0f, 0f, w, h, bgPaint)
            textPaint.color = Color.CYAN
            textPaint.textSize = unit * 0.72f
            canvas.drawText("GAME OVER - LV$currentLevel", w / 2f, h * 0.42f, textPaint)
            textPaint.color = Color.YELLOW
            textPaint.textSize = unit * 0.46f
            canvas.drawText("BEST: ${getLevelBestScore(currentLevel)} | TAP OR ARROW", w / 2f, h * 0.62f, textPaint)
        } else if (waiting) {
            bgPaint.color = Color.argb(165, 0, 0, 0)
            canvas.drawRect(0f, 0f, w, h, bgPaint)
            textPaint.color = Color.CYAN
            textPaint.textSize = unit * 0.52f
            canvas.drawText(cfg.name.uppercase(), w / 2f, h * 0.38f, textPaint)
            textPaint.color = Color.GREEN
            textPaint.textSize = unit * 0.42f
            canvas.drawText("SWIPE OR ARROW TO START", w / 2f, h * 0.58f, textPaint)
        } else if (paused) {
            bgPaint.color = Color.argb(145, 0, 0, 0)
            canvas.drawRect(0f, 0f, w, h, bgPaint)
            textPaint.color = Color.CYAN
            textPaint.textSize = unit * 0.72f
            canvas.drawText("PAUSED", w / 2f, h * 0.52f, textPaint)
        }
    }

    private fun drawFood(canvas: Canvas, cx: Float, cy: Float, r: Float, item: GameData.FoodItem) {
        shapePaint.color = Color.parseColor(item.colorHex)
        strokePaint.color = Color.parseColor(item.colorHex)
        when (item.shape) {
            "square" -> canvas.drawRect(RectF(cx - r, cy - r, cx + r, cy + r), shapePaint)
            "diamond" -> {
                val path = android.graphics.Path().apply {
                    moveTo(cx, cy - r * 1.2f)
                    lineTo(cx + r * 1.1f, cy)
                    lineTo(cx, cy + r * 1.2f)
                    lineTo(cx - r * 1.1f, cy)
                    close()
                }
                canvas.drawPath(path, shapePaint)
            }
            "triangle" -> {
                val path = android.graphics.Path().apply {
                    moveTo(cx, cy - r * 1.1f)
                    lineTo(cx + r * 1.1f, cy + r)
                    lineTo(cx - r * 1.1f, cy + r)
                    close()
                }
                canvas.drawPath(path, shapePaint)
            }
            "star" -> {
                val path = android.graphics.Path()
                for (i in 0 until 10) {
                    val rad = if (i % 2 == 0) r * 1.2f else r * 0.55f
                    val ang = i * PI / 5.0 - PI / 2.0
                    val px = cx + (cos(ang) * rad).toFloat()
                    val py = cy + (sin(ang) * rad).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, shapePaint)
            }
            else -> {
                canvas.drawCircle(cx, cy, r, shapePaint)
                canvas.drawCircle(cx, cy, r * 0.45f, strokePaint)
            }
        }
    }

    private fun drawPowerCoin(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        shapePaint.color = Color.parseColor("#ffcc00")
        canvas.drawCircle(cx, cy, r, shapePaint)
        strokePaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, r * 1.15f, strokePaint)
        textPaint.color = Color.BLACK
        textPaint.textSize = max(9f, r * 0.72f)
        canvas.drawText("PC", cx, cy + textPaint.textSize * 0.35f, textPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(tickRunnable)
        try {
            toneGen?.release()
        } catch (_: Exception) {
        }
        toneGen = null
    }
}
