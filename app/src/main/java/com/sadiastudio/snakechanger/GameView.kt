package com.sadiastudio.snakechanger

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class GameView(
    context: Context,
    private val levelIndex: Int,
    private val level: LevelConfig,
    foodIndexRaw: Int,
    bodyIndexRaw: Int,
    fullIndexRaw: Int,
    headIndexRaw: Int,
    private val soundOn: Boolean = true,
    private val vibrationOn: Boolean = true,
    private val trailOn: Boolean = true,
    private val onScore: (score: Int, earned: Int) -> Unit,
    private val onExit: () -> Unit
) : View(context) {

    private val foodItem: FoodItem = GameData.safeFood(foodIndexRaw)
    private val bodyItem: BodyItem = GameData.safeBody(bodyIndexRaw)
    private val fullBodyItem: FullBody = GameData.safeFull(fullIndexRaw)
    private val headItem: HeadItem = GameData.safeHead(headIndexRaw)

    private val cols = 20
    private val rows = 10
    private var cellSize = 40f
    private var boardLeft = 0f
    private var boardTop = 120f

    private var snake = mutableListOf(Pair(8, 5), Pair(7, 5), Pair(6, 5), Pair(5, 5))
    private var dirX = 1
    private var dirY = 0
    private var pendingDirX = 1
    private var pendingDirY = 0

    private var normalFood = Pair(14, 4)
    private var specialCoin: Pair<Int, Int>? = null
    private var specialTimer = 0
    private var normalFoodCount = 0

    private var score = 0
    private var earnedCoins = 0
    private var isGameOver = false
    private var isPaused = false

    private val trail = mutableListOf<Pair<Float, Float>>()
    private var touchStartX = 0f
    private var touchStartY = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0B0B0")
        textSize = 21f
        textAlign = Paint.Align.CENTER
    }

    private val stepDelayMs: Long
        get() {
            val base = 200L
            val levelReduce = (levelIndex + 1) * 4L
            val bonus = level.speedBonus.toLong()
            val scoreReduce = (score / 3).toLong()
            return (base - levelReduce - bonus - scoreReduce).coerceAtLeast(80L)
        }

    private val gameLoopRunnable = object : Runnable {
        override fun run() {
            if (!isGameOver && !isPaused) {
                stepGame()
                invalidate()
                postDelayed(this, stepDelayMs)
            }
        }
    }

    init {
        spawnNormalFood()
        resumeGame()
    }

    fun pauseGame() {
        if (!isPaused) {
            isPaused = true
            removeCallbacks(gameLoopRunnable)
            invalidate()
        }
    }

    fun resumeGame() {
        if (isGameOver) return
        isPaused = false
        removeCallbacks(gameLoopRunnable)
        postDelayed(gameLoopRunnable, stepDelayMs)
        invalidate()
    }

    fun restartGame() {
        removeCallbacks(gameLoopRunnable)
        snake = mutableListOf(Pair(8, 5), Pair(7, 5), Pair(6, 5), Pair(5, 5))
        dirX = 1
        dirY = 0
        pendingDirX = 1
        pendingDirY = 0
        score = 0
        earnedCoins = 0
        normalFoodCount = 0
        specialCoin = null
        specialTimer = 0
        isGameOver = false
        isPaused = false
        trail.clear()
        spawnNormalFood()
        postDelayed(gameLoopRunnable, stepDelayMs)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(gameLoopRunnable)
    }

    private fun spawnNormalFood() {
        var attempts = 0
        while (attempts < 100) {
            val candidate = Pair(Random.nextInt(1, cols - 1), Random.nextInt(1, rows - 1))
            if (!snake.contains(candidate) && candidate != specialCoin) {
                normalFood = candidate
                return
            }
            attempts++
        }
    }

    private fun spawnSpecialCoin() {
        var attempts = 0
        while (attempts < 100) {
            val candidate = Pair(Random.nextInt(1, cols - 1), Random.nextInt(1, rows - 1))
            if (!snake.contains(candidate) && candidate != normalFood) {
                specialCoin = candidate
                specialTimer = 50
                return
            }
            attempts++
        }
    }

    private fun stepGame() {
        dirX = pendingDirX
        dirY = pendingDirY

        val currentHead = snake.first()
        val nextHead = Pair(currentHead.first + dirX, currentHead.second + dirY)

        if (nextHead.first < 0 || nextHead.first >= cols || nextHead.second < 0 || nextHead.second >= rows) {
            triggerGameOver()
            return
        }

        val willEatNormal = nextHead == normalFood
        val willEatSpecial = specialCoin != null && nextHead == specialCoin
        val bodyToCheck = if (willEatNormal || willEatSpecial) snake else snake.dropLast(1)
        if (bodyToCheck.contains(nextHead)) {
            triggerGameOver()
            return
        }

        snake.add(0, nextHead)

        if (willEatSpecial) {
            score += 1
            earnedCoins += GameData.SPECIAL_EARN
            specialCoin = null
            specialTimer = 0
            normalFoodCount = 0
            spawnNormalFood()
            playEatSound()
            vibrateDevice(110L)
        } else if (willEatNormal) {
            score += 1
            earnedCoins += GameData.FOOD_EARN
            normalFoodCount++
            if (normalFoodCount % 10 == 0 && specialCoin == null) {
                spawnSpecialCoin()
            }
            spawnNormalFood()
            playEatSound()
            vibrateDevice(55L)
        } else {
            snake.removeAt(snake.size - 1)
        }

        if (specialTimer > 0) {
            specialTimer--
            if (specialTimer == 0) {
                specialCoin = null
            }
        }

        if (trailOn) {
            val centerX = boardLeft + (nextHead.first + 0.5f) * cellSize
            val centerY = boardTop + (nextHead.second + 0.5f) * cellSize
            trail.add(Pair(centerX, centerY))
            if (trail.size > 8) {
                trail.removeAt(0)
            }
        } else {
            trail.clear()
        }
    }

    private fun triggerGameOver() {
        if (isGameOver) return
        isGameOver = true
        isPaused = true
        removeCallbacks(gameLoopRunnable)
        invalidate()
        playGameOverSound()
        vibrateDevice(250L)
        postDelayed({ onScore(score, earnedCoins) }, 750L)
    }

    private fun playEatSound() {
        if (!soundOn) return
        // Hook for SoundPool eat sound
    }

    private fun playGameOverSound() {
        if (!soundOn) return
        // Hook for SoundPool game over sound
    }

    private fun vibrateDevice(durationMs: Long) {
        if (!vibrationOn) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {
            // Ignore vibration errors
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val topReserved = 120f
        val bottomReserved = 140f
        val availableHeight = (h - topReserved - bottomReserved).coerceAtLeast(180f)
        val availableWidth = (w - 24f).coerceAtLeast(240f)
        cellSize = min(availableWidth / cols, availableHeight / rows)
        boardLeft = (w - cellSize * cols) / 2f
        boardTop = topReserved
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
                if (abs(dx) < 24f && abs(dy) < 24f) {
                    if (event.y < boardTop) {
                        if (isPaused) resumeGame() else pauseGame()
                    }
                    return true
                }

                var newDirX = dirX
                var newDirY = dirY
                if (abs(dx) > abs(dy)) {
                    if (dx > 0f && dirX != -1) {
                        newDirX = 1; newDirY = 0
                    } else if (dx < 0f && dirX != 1) {
                        newDirX = -1; newDirY = 0
                    }
                } else {
                    if (dy > 0f && dirY != -1) {
                        newDirX = 0; newDirY = 1
                    } else if (dy < 0f && dirY != 1) {
                        newDirX = 0; newDirY = -1
                    }
                }

                pendingDirX = newDirX
                pendingDirY = newDirY
                if (isPaused && !isGameOver) {
                    resumeGame()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#0A0A0A"))

        paint.style = Paint.Style.FILL
        paint.color = parseSafeColor(level.bg, Color.BLACK)
        canvas.drawRect(boardLeft, boardTop, boardLeft + cols * cellSize, boardTop + rows * cellSize, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.parseColor("#00FFFF")
        canvas.drawRect(boardLeft, boardTop, boardLeft + cols * cellSize, boardTop + rows * cellSize, paint)

        if (trailOn && trail.isNotEmpty()) {
            val trailColor = parseSafeColor(level.trail, Color.parseColor("#FF88CC"))
            paint.style = Paint.Style.FILL
            trail.forEachIndexed { idx, point ->
                paint.color = trailColor
                paint.alpha = ((idx + 1) * 110 / trail.size).coerceIn(25, 130)
                canvas.drawCircle(point.first, point.second, cellSize * 0.22f, paint)
            }
            paint.alpha = 255
        }

        drawFoodShape(canvas, normalFood, foodItem)

        specialCoin?.let { coinPos ->
            val cx = boardLeft + (coinPos.first + 0.5f) * cellSize
            val cy = boardTop + (coinPos.second + 0.5f) * cellSize
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#FFCC00")
            canvas.drawCircle(cx, cy, cellSize * 0.44f, paint)
            paint.color = Color.BLACK
            paint.textSize = cellSize * 0.34f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("SGS", cx, cy + cellSize * 0.11f, paint)
        }

        snake.forEachIndexed { index, segment ->
            val cx = boardLeft + (segment.first + 0.5f) * cellSize
            val cy = boardTop + (segment.second + 0.5f) * cellSize
            if (index == 0) {
                paint.style = Paint.Style.FILL
                paint.color = parseSafeColor(headItem.color, Color.YELLOW)
                canvas.drawCircle(cx, cy, cellSize * 0.45f, paint)
                paint.color = Color.BLACK
                paint.textSize = cellSize * 0.42f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(headItem.emoji, cx, cy + cellSize * 0.14f, paint)
            } else {
                drawSegment(canvas, cx, cy, index)
            }
        }

        hudPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Score: $score  |  ${stepDelayMs}ms", 20f, 52f, hudPaint)
        hudPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Coins: +$earnedCoins", width - 20f, 52f, hudPaint)

        val statusText = when {
            isGameOver -> "Game Over - ${fullBodyItem.name}"
            isPaused -> "Paused - Tap Top or Swipe to Resume"
            else -> "Tap Top to Pause | Swipe to Steer"
        }
        canvas.drawText(statusText, width / 2f, 92f, hintPaint)

        if (isGameOver) {
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(195, 0, 0, 0)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            hudPaint.textAlign = Paint.Align.CENTER
            hudPaint.textSize = 44f
            canvas.drawText("GAME OVER - SNAKE CHANGER - SGS", width / 2f, height / 2f - 20f, hudPaint)

            hintPaint.textSize = 26f
            canvas.drawText("Score: $score | Earned: +$earnedCoins | ${GameData.STUDIO}", width / 2f, height / 2f + 30f, hintPaint)
            hudPaint.textSize = 28f
            hintPaint.textSize = 21f
        }
    }

    private fun drawSegment(canvas: Canvas, cx: Float, cy: Float, index: Int) {
        val radius = (cellSize * (fullBodyItem.size / 9f)).coerceIn(cellSize * 0.25f, cellSize * 0.44f)
        paint.style = Paint.Style.FILL
        if (index % 2 == 0) {
            paint.color = parseSafeColor(fullBodyItem.color, Color.GREEN)
            canvas.drawCircle(cx, cy, radius, paint)
            paint.color = parseSafeColor(fullBodyItem.center, Color.WHITE)
            canvas.drawCircle(cx, cy, radius * 0.35f, paint)
        } else {
            paint.color = parseSafeColor(bodyItem.color, Color.CYAN)
            canvas.drawCircle(cx, cy, radius * 0.9f, paint)
        }
    }

    private fun drawFoodShape(canvas: Canvas, cell: Pair<Int, Int>, item: FoodItem) {
        val cx = boardLeft + (cell.first + 0.5f) * cellSize
        val cy = boardTop + (cell.second + 0.5f) * cellSize
        val r = cellSize * 0.38f
        paint.style = Paint.Style.FILL
        paint.color = parseSafeColor(item.color, Color.RED)

        when (item.shape) {
            "apple" -> {
                canvas.drawCircle(cx, cy, r, paint)
                paint.color = Color.parseColor("#00FF66")
                canvas.drawRect(cx - 2f, cy - r - 6f, cx + 2f, cy - r + 2f, paint)
            }
            "star", "crown" -> {
                val path = Path()
                for (i in 0 until 10) {
                    val rad = if (i % 2 == 0) r else r * 0.45f
                    val angle = Math.PI / 5.0 * i - Math.PI / 2.0
                    val px = (cx + rad * cos(angle)).toFloat()
                    val py = (cy + rad * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            "diamond" -> {
                val path = Path().apply {
                    moveTo(cx, cy - r)
                    lineTo(cx + r, cy)
                    lineTo(cx, cy + r)
                    lineTo(cx - r, cy)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            "heart" -> {
                canvas.drawCircle(cx - r * 0.45f, cy - r * 0.2f, r * 0.5f, paint)
                canvas.drawCircle(cx + r * 0.45f, cy - r * 0.2f, r * 0.5f, paint)
                val path = Path().apply {
                    moveTo(cx - r * 0.9f, cy - r * 0.05f)
                    lineTo(cx + r * 0.9f, cy - r * 0.05f)
                    lineTo(cx, cy + r)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            "triangle" -> {
                val path = Path().apply {
                    moveTo(cx, cy - r)
                    lineTo(cx + r, cy + r)
                    lineTo(cx - r, cy + r)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            "square", "cross" -> {
                canvas.drawRect(cx - r, cy - r, cx + r, cy + r, paint)
            }
            "hexagon" -> {
                val path = Path()
                for (i in 0 until 6) {
                    val angle = Math.PI / 3.0 * i
                    val px = (cx + r * cos(angle)).toFloat()
                    val py = (cy + r * sin(angle)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            "moon" -> {
                canvas.drawCircle(cx, cy, r, paint)
                paint.color = parseSafeColor(level.bg, Color.BLACK)
                canvas.drawCircle(cx + r * 0.4f, cy - r * 0.2f, r * 0.75f, paint)
            }
            "drop" -> {
                val path = Path().apply {
                    moveTo(cx, cy - r)
                    quadTo(cx + r, cy + r * 0.2f, cx, cy + r)
                    quadTo(cx - r, cy + r * 0.2f, cx, cy - r)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            "chilli", "lemon" -> {
                canvas.drawOval(RectF(cx - r, cy - r * 0.65f, cx + r, cy + r * 0.65f), paint)
            }
            else -> {
                canvas.drawCircle(cx, cy, r, paint)
            }
        }
    }

    private fun parseSafeColor(colorStr: String, fallback: Int): Int {
        return try {
            Color.parseColor(colorStr)
        } catch (_: Exception) {
            fallback
        }
    }
}
