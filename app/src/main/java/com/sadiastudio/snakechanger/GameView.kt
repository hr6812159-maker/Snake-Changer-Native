package com.sadiastudio.snakechanger

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Direction { UP, DOWN, LEFT, RIGHT }

    interface Listener {
        fun onCoinsEarned(newCoins: Int, isSpecial: Boolean)
        fun onScoreUpdated(score: Int, highScore: Int, speedMs: Long)
        fun onGameOver(finalScore: Int, highScore: Int)
        fun onPauseChanged(paused: Boolean, waiting: Boolean)
        fun onWinLevel()
    }

    var listener: Listener? = null

    private val cols = 20
    private val rows = 10

    private var snake = mutableListOf(Pair(8, 5), Pair(7, 5), Pair(6, 5), Pair(5, 5))
    private var dir = Direction.RIGHT
    private var nextDir = Direction.RIGHT
    private var food = Pair(14, 4)
    private var specialFood: Pair<Int, Int>? = null
    private var normalFoodCount = 0

    private var trail = mutableListOf<Triple<Int, Int, Int>>()
    private var trailOn = true

    private var score = 0
    private var highScore = 0
    private var coins = GameData.START_COINS
    private var curLv = 1

    private var selFood = 0
    private var selBody = 0
    private var selFull = 0
    private var selHead = 0

    var waiting = true
        private set
    var paused = true
        private set
    var isGameOver = false
        private set

    private var touchStartX = 0f
    private var touchStartY = 0f

    private val handler = Handler(Looper.getMainLooper())
    private val loopRunnable = object : Runnable {
        override fun run() {
            if (!paused && !waiting && !isGameOver) {
                stepGame()
                invalidate()
                if (!paused && !waiting && !isGameOver) {
                    handler.postDelayed(this, getSpeedMs())
                }
            }
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF2A55")
        strokeWidth = 4.5f
        style = Paint.Style.STROKE
    }

    fun configure(
        level: Int,
        coins: Int,
        highScore: Int,
        trailOn: Boolean,
        foodIdx: Int,
        bodyIdx: Int,
        fullIdx: Int,
        headIdx: Int
    ) {
        this.curLv = level.coerceIn(1, 15)
        this.coins = coins.coerceAtLeast(0)
        this.highScore = highScore.coerceAtLeast(0)
        this.trailOn = trailOn
        this.selFood = foodIdx.coerceIn(0, GameData.foodItems.lastIndex)
        this.selBody = bodyIdx.coerceIn(0, GameData.bodyItems.lastIndex)
        this.selFull = fullIdx.coerceIn(0, GameData.fullBodies.lastIndex)
        this.selHead = headIdx.coerceIn(0, GameData.heads.lastIndex)
        resetForLevel(keepWaiting = true)
    }

    fun applyTheme(level: Int, trailOn: Boolean, foodIdx: Int, bodyIdx: Int, fullIdx: Int, headIdx: Int) {
        this.curLv = level.coerceIn(1, 15)
        this.trailOn = trailOn
        if (!trailOn) trail.clear()
        this.selFood = foodIdx.coerceIn(0, GameData.foodItems.lastIndex)
        this.selBody = bodyIdx.coerceIn(0, GameData.bodyItems.lastIndex)
        this.selFull = fullIdx.coerceIn(0, GameData.fullBodies.lastIndex)
        this.selHead = headIdx.coerceIn(0, GameData.heads.lastIndex)
        listener?.onScoreUpdated(score, highScore, getSpeedMs())
        invalidate()
    }

    fun setCoinsValue(newCoins: Int) {
        coins = newCoins.coerceAtLeast(0)
    }

    fun getSpeedMs(): Long {
        val cfg = GameData.safeLevel(curLv)
        val base = 200L
        val levelReduce = curLv * 4L
        val bonus = cfg.speedBonus.toLong()
        val scoreReduce = (score / 3).toLong()
        return (base - levelReduce - bonus - scoreReduce).coerceAtLeast(80L)
    }

    fun resetForLevel(keepWaiting: Boolean = true) {
        handler.removeCallbacks(loopRunnable)
        snake = mutableListOf(Pair(8, 5), Pair(7, 5), Pair(6, 5), Pair(5, 5))
        trail.clear()
        dir = Direction.RIGHT
        nextDir = Direction.RIGHT
        score = 0
        normalFoodCount = 0
        specialFood = null
        isGameOver = false
        waiting = keepWaiting
        paused = true
        placeNormalFood()
        listener?.onScoreUpdated(score, highScore, getSpeedMs())
        listener?.onPauseChanged(paused, waiting)
        invalidate()
    }

    fun pauseForMenu() {
        if (!waiting && !isGameOver) {
            paused = true
            handler.removeCallbacks(loopRunnable)
            listener?.onPauseChanged(paused, waiting)
            invalidate()
        }
    }

    fun resumeGame() {
        if (isGameOver) return
        paused = false
        waiting = false
        handler.removeCallbacks(loopRunnable)
        handler.postDelayed(loopRunnable, getSpeedMs())
        listener?.onPauseChanged(paused, waiting)
        invalidate()
    }

    fun togglePauseOrStart() {
        if (isGameOver) {
            isGameOver = false
            resetForLevel(keepWaiting = false)
            return
        }
        if (waiting) {
            resumeGame()
            return
        }
        if (paused) {
            resumeGame()
        } else {
            paused = true
            handler.removeCallbacks(loopRunnable)
            listener?.onPauseChanged(paused, waiting)
            invalidate()
        }
    }

    fun setDirection(newDir: Direction) {
        val valid = (newDir == Direction.UP && dir != Direction.DOWN) ||
            (newDir == Direction.DOWN && dir != Direction.UP) ||
            (newDir == Direction.LEFT && dir != Direction.RIGHT) ||
            (newDir == Direction.RIGHT && dir != Direction.LEFT)
        if (!valid) return
        nextDir = newDir
        if (waiting || paused || isGameOver) {
            if (isGameOver) {
                isGameOver = false
                resetForLevel(keepWaiting = false)
            } else {
                resumeGame()
            }
        }
    }

    private fun placeNormalFood() {
        var attempts = 0
        while (attempts < 100) {
            val fx = Random.nextInt(1, 19)
            val fy = Random.nextInt(1, 9)
            val cand = Pair(fx, fy)
            if (cand !in snake && cand != specialFood) {
                food = cand
                return
            }
            attempts++
        }
        isGameOver = true
        paused = true
        handler.removeCallbacks(loopRunnable)
        listener?.onWinLevel()
    }

    private fun placeSpecialFoodSafe() {
        var attempts = 0
        while (attempts < 100) {
            val sx = Random.nextInt(1, 19)
            val sy = Random.nextInt(1, 9)
            val cand = Pair(sx, sy)
            if (cand !in snake && cand != food && cand != specialFood) {
                specialFood = cand
                return
            }
            attempts++
        }
    }

    private fun stepGame() {
        dir = nextDir
        val head = snake.first()
        val nextHead = when (dir) {
            Direction.UP -> Pair(head.first, head.second - 1)
            Direction.DOWN -> Pair(head.first, head.second + 1)
            Direction.LEFT -> Pair(head.first - 1, head.second)
            Direction.RIGHT -> Pair(head.first + 1, head.second)
        }

        val cfg = GameData.safeLevel(curLv)
        val trailColor = Color.parseColor(cfg.trailHex)
        if (trailOn) {
            trail.add(0, Triple(nextHead.first, nextHead.second, trailColor))
            if (trail.size > 8) trail.removeAt(trail.lastIndex)
        } else {
            trail.clear()
        }

        val hitWall = nextHead.first !in 0 until cols || nextHead.second !in 0 until rows
        val hitSelf = nextHead in snake
        if (hitWall || hitSelf) {
            handler.removeCallbacks(loopRunnable)
            paused = true
            waiting = true
            isGameOver = true
            if (score > highScore) {
                highScore = score
            }
            listener?.onScoreUpdated(score, highScore, getSpeedMs())
            listener?.onGameOver(score, highScore)
            listener?.onPauseChanged(paused, waiting)
            return
        }

        snake.add(0, nextHead)

        if (specialFood != null && nextHead == specialFood) {
            score += 1
            coins += GameData.SPECIAL_FOOD_EARN
            specialFood = null
            normalFoodCount = 0
            placeNormalFood()
            listener?.onCoinsEarned(coins, true)
        } else if (nextHead == food) {
            score += 1
            coins += GameData.FOOD_EARN
            normalFoodCount += 1
            if (normalFoodCount % 10 == 0) {
                placeSpecialFoodSafe()
            }
            placeNormalFood()
            listener?.onCoinsEarned(coins, false)
        } else {
            snake.removeAt(snake.lastIndex)
        }

        if (score > highScore) {
            highScore = score
        }
        listener?.onScoreUpdated(score, highScore, getSpeedMs())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cfg = GameData.safeLevel(curLv)
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor(cfg.bgHex)
        canvas.drawRect(0f, 0f, w, h, paint)

        val cellW = w / cols
        val cellH = h / rows
        val cellMin = minOf(cellW, cellH)

        if (trailOn && trail.isNotEmpty()) {
            trail.forEach { t ->
                val tx = t.first * cellW + cellW / 2f
                val ty = t.second * cellH + cellH / 2f
                paint.style = Paint.Style.FILL
                paint.color = t.third
                canvas.drawCircle(tx, ty, cellMin * 0.14f, paint)
            }
        }

        if (waiting && !isGameOver) {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#B8000000")
            canvas.drawRect(0f, 0f, w, h, paint)

            paint.color = Color.parseColor("#00FFFF")
            paint.textSize = (h * 0.085f).coerceIn(10f, 16f)
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            canvas.drawText(cfg.name.uppercase(), w / 2f, h * 0.30f, paint)

            paint.color = Color.parseColor("#00FF88")
            paint.textSize = (h * 0.07f).coerceIn(9f, 14f)
            canvas.drawText("PRESS ARROW OR SWIPE TO START", w / 2f, h * 0.50f, paint)
            canvas.drawRect(1.5f, 1.5f, w - 1.5f, h - 1.5f, borderPaint)
            return
        }

        if (paused && !waiting && !isGameOver) {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#80000000")
            canvas.drawRect(0f, 0f, w, h, paint)

            paint.color = Color.parseColor("#00FFFF")
            paint.textSize = (h * 0.11f).coerceIn(13f, 20f)
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            canvas.drawText("PAUSED - SNAKE CHANGER", w / 2f, h * 0.53f, paint)
            canvas.drawRect(1.5f, 1.5f, w - 1.5f, h - 1.5f, borderPaint)
            return
        }

        if (isGameOver) {
            val fullCfg = GameData.fullBodies[selFull]
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#D9000000")
            canvas.drawRect(0f, 0f, w, h, paint)

            paint.color = Color.parseColor("#00FFFF")
            paint.textSize = (h * 0.105f).coerceIn(12f, 19f)
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            canvas.drawText("GAME OVER - SNAKE CHANGER - SGS", w / 2f, h * 0.42f, paint)

            paint.color = Color.parseColor("#FFEB3B")
            paint.textSize = (h * 0.068f).coerceIn(8.5f, 13f)
            canvas.drawText(
                "PRESS ARROW - ${fullCfg.name.uppercase()} - ${GameData.STUDIO}",
                w / 2f,
                h * 0.58f,
                paint
            )
            canvas.drawRect(1.5f, 1.5f, w - 1.5f, h - 1.5f, borderPaint)
            return
        }

        val foodItem = GameData.foodItems[selFood]
        drawFoodShape(canvas, food.first * cellW + cellW / 2f, food.second * cellH + cellH / 2f, cellMin, foodItem)

        specialFood?.let { sp ->
            drawSpecialCoin(canvas, sp.first * cellW + cellW / 2f, sp.second * cellH + cellH / 2f, cellMin)
        }

        val fullCfg = GameData.fullBodies[selFull]
        val headCfg = GameData.heads[selHead]
        val bodyCfg = GameData.bodyItems[selBody]

        snake.forEachIndexed { idx, seg ->
            val sx = seg.first * cellW + cellW / 2f
            val sy = seg.second * cellH + cellH / 2f
            if (idx == 0) {
                paint.reset()
                paint.isAntiAlias = true
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor(headCfg.colorHex)
                canvas.drawCircle(sx, sy, cellMin * 0.40f, paint)

                paint.color = Color.BLACK
                paint.textSize = (cellMin * 0.42f).coerceIn(8f, 14f)
                paint.textAlign = Paint.Align.CENTER
                paint.isFakeBoldText = true
                canvas.drawText(headCfg.symbol, sx, sy + (paint.textSize * 0.35f), paint)
            } else if (idx % 2 == 0) {
                drawCompactSegment(canvas, sx, sy, cellMin, fullCfg)
            } else {
                drawBodySegment(canvas, sx, sy, cellMin, bodyCfg, idx)
            }
        }

        canvas.drawRect(1.5f, 1.5f, w - 1.5f, h - 1.5f, borderPaint)
    }

    private fun drawFoodShape(canvas: Canvas, x: Float, y: Float, unit: Float, item: GameData.FoodItem) {
        val r = unit * 0.36f
        val col = Color.parseColor(item.colorHex)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = col

        when (item.shape) {
            "apple" -> {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x, y, r, paint)
                paint.color = Color.parseColor("#00FF00")
                canvas.drawRect(x - r * 0.15f, y - r * 1.3f, x + r * 0.15f, y - r * 0.7f, paint)
            }
            "star", "crown" -> {
                paint.style = Paint.Style.FILL
                val path = Path()
                for (i in 0 until 5) {
                    val ang = i * Math.PI * 2.0 / 5.0 - Math.PI / 2.0
                    val px = x + (cos(ang) * r).toFloat()
                    val py = y + (sin(ang) * r).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            "diamond" -> {
                paint.style = Paint.Style.FILL
                val path = Path().apply {
                    moveTo(x, y - r * 1.2f)
                    lineTo(x + r * 1.1f, y)
                    lineTo(x, y + r * 1.2f)
                    lineTo(x - r * 1.1f, y)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            "triangle" -> {
                paint.style = Paint.Style.FILL
                val path = Path().apply {
                    moveTo(x, y - r * 1.1f)
                    lineTo(x + r * 1.1f, y + r)
                    lineTo(x - r * 1.1f, y + r)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            "square", "cross" -> {
                paint.style = Paint.Style.FILL
                canvas.drawRect(RectF(x - r, y - r, x + r, y + r), paint)
            }
            "hexagon" -> {
                paint.style = Paint.Style.FILL
                val path = Path()
                for (i in 0 until 6) {
                    val ang = i * Math.PI / 3.0
                    val px = x + (cos(ang) * r).toFloat()
                    val py = y + (sin(ang) * r).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            "rainbow" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.color = Color.parseColor("#FF00FF")
                canvas.drawArc(RectF(x - r, y - r, x + r, y + r), 180f, 180f, false, paint)
            }
            else -> {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x, y, r, paint)
            }
        }
    }

    private fun drawSpecialCoin(canvas: Canvas, x: Float, y: Float, unit: Float) {
        val r = unit * 0.42f
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#FFCC00")
        canvas.drawCircle(x, y, r, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.WHITE
        canvas.drawCircle(x, y, r * 1.15f, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.textSize = (unit * 0.34f).coerceIn(7f, 12f)
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        canvas.drawText("SGS", x, y + (paint.textSize * 0.35f), paint)
    }

    private fun drawBodySegment(canvas: Canvas, x: Float, y: Float, unit: Float, item: GameData.BodyItem, idx: Int) {
        val r = unit * 0.28f
        val col = Color.parseColor(item.colorHex)
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = if (item.pattern == "rainbow") {
            if (idx % 2 == 0) Color.parseColor("#FF00FF") else Color.parseColor("#00FFFF")
        } else {
            col
        }
        canvas.drawCircle(x, y, r, paint)
    }

    private fun drawCompactSegment(canvas: Canvas, x: Float, y: Float, unit: Float, fb: GameData.FullBodyItem) {
        val r = (unit * 0.18f) + (fb.size * 0.45f)
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor(fb.dotHex)
        canvas.drawCircle(x, y, r, paint)

        paint.color = Color.parseColor(fb.centerHex)
        canvas.drawCircle(x, y, r * 0.45f, paint)
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
                } else {
                    togglePauseOrStart()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(loopRunnable)
    }
}
