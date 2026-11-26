package com.sadiastudio.snakechanger

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("sgs_snake_changer_prefs", Context.MODE_PRIVATE)
    }

    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var adManager: AdManager
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var gameView: GameView

    private lateinit var playerIdText: TextView
    private lateinit var unlockInfoText: TextView
    private lateinit var curLvBadgeText: TextView
    private lateinit var coinTopText: TextView
    private lateinit var scoreLvTopText: TextView
    private lateinit var speedTopText: TextView
    private lateinit var soundTopText: TextView

    private lateinit var loadingOverlay: FrameLayout
    private lateinit var stepViews: List<TextView>
    private lateinit var progressBarFill: View
    private lateinit var loadPercentText: TextView
    private lateinit var tapToEnterBtn: Button

    private lateinit var panelContainer: FrameLayout
    private var loadingTimer: Runnable? = null
    private var loadProgress = 0
    private var loadingFinished = false
    private var wheelAnimator: ValueAnimator? = null

    private var coins: Int = GameData.START_COINS
    private var highScore: Int = 0
    private var currentLevel: Int = 1
    private var selectedFood: Int = 0
    private var selectedBody: Int = 0
    private var selectedFull: Int = 0
    private var selectedHead: Int = 0

    private var soundOn: Boolean = true
    private var vibOn: Boolean = true
    private var trailOn: Boolean = true
    private var playerId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadSavedState()
        adManager = AdManager(this)

        val root = buildRootLayout()
        setContentView(root)

        adManager.triggerTopBannerAd()
        syncAllUi()
        startFiveStepLoading()
    }

    private fun loadSavedState() {
        val savedPid = prefs.getString("sgs_player_id", null)
        playerId = if (!savedPid.isNullOrBlank() && savedPid.startsWith("SGS-")) {
            savedPid
        } else {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            val randomPart = buildString {
                repeat(6) { append(chars[Random.nextInt(chars.length)]) }
            }
            "SGS-$randomPart".also { prefs.edit().putString("sgs_player_id", it).apply() }
        }

        val encCoins = prefs.getString("sgs_secure_c", null)
        coins = if (encCoins == null) {
            val legacy = prefs.getInt("c", GameData.START_COINS).coerceAtLeast(0)
            GameData.encodeCoins(legacy).also { prefs.edit().putString("sgs_secure_c", it).apply() }
            legacy
        } else {
            GameData.decodeCoins(encCoins)
        }

        highScore = prefs.getInt("h", 0).coerceAtLeast(0)
        currentLevel = prefs.getInt("curLv", 1).coerceIn(1, 15)
        if (!isLevelUnlocked(currentLevel)) {
            currentLevel = 1
        }

        selectedFood = prefs.getInt("sel_food", 0).coerceIn(0, GameData.foodItems.size - 1)
        selectedBody = prefs.getInt("sel_body", 0).coerceIn(0, GameData.bodyItems.size - 1)
        selectedFull = prefs.getInt("sel_full", 0).coerceIn(0, GameData.fullBodies.size - 1)
        selectedHead = prefs.getInt("sel_head", 0).coerceIn(0, GameData.heads.size - 1)

        soundOn = prefs.getBoolean("soundOn", true)
        vibOn = prefs.getBoolean("vibOn", true)
        trailOn = prefs.getBoolean("trailOn", true)
    }

    private fun updateCoins(newValue: Int) {
        coins = newValue.coerceAtLeast(0)
        prefs.edit().putString("sgs_secure_c", GameData.encodeCoins(coins)).apply()
        syncAllUi()
    }

    private fun isLevelUnlocked(level: Int): Boolean {
        if (level <= 1) return true
        return prefs.getBoolean("lvUnlock_$level", false)
    }

    private fun highestUnlockedLevel(): Int {
        var maxLv = 1
        for (lv in 1..15) {
            if (isLevelUnlocked(lv)) maxLv = lv
        }
        return maxLv
    }

    private fun isItemUnlocked(prefix: String, index: Int): Boolean {
        return prefs.getBoolean("${prefix}_$index", false)
    }

    private fun unlockItem(prefix: String, index: Int) {
        prefs.edit().putBoolean("${prefix}_$index", true).apply()
    }

    private fun buildRootLayout(): FrameLayout {
        val hostFrame = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#080810"))
        }

        drawerLayout = DrawerLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setScrimColor(Color.argb(170, 0, 0, 0))
            addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerOpened(drawerView: View) {
                    adManager.setTopBannerVisible(false)
                }

                override fun onDrawerClosed(drawerView: View) {
                    if (panelContainer.visibility != View.VISIBLE && loadingOverlay.visibility != View.VISIBLE) {
                        adManager.setTopBannerVisible(true)
                    }
                }
            })
        }

        val mainColumn = LinearLayout(this).apply {
            layoutParams = DrawerLayout.LayoutParams(
                DrawerLayout.LayoutParams.MATCH_PARENT,
                DrawerLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(44), dp(10), dp(36))
            setBackgroundColor(Color.parseColor("#080810"))
        }

        val topSpaceAndNav = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)).apply {
                bottomMargin = dp(8)
            }
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
        }

        val topNavRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(24)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val leftMenuBtn = makeSmallActionBtn("MENU") {
            adManager.setTopBannerVisible(false)
            drawerLayout.openDrawer(GravityCompat.START)
        }
        val topSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(1), 1f)
        }
        val rightSetBtn = makeSmallActionBtn("SET") {
            adManager.setTopBannerVisible(false)
            drawerLayout.openDrawer(GravityCompat.END)
        }
        topNavRow.addView(leftMenuBtn)
        topNavRow.addView(topSpacer)
        topNavRow.addView(rightSetBtn)
        topSpaceAndNav.addView(topNavRow)
        mainColumn.addView(topSpaceAndNav)

        val playerBox = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(360), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = makeBoxDrawable("#111111", "#00ffff", 10f, 2)
            setOnClickListener { openProfilePanel() }
        }

        val avatarView = object : View(this) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG)
            private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                p.color = Color.parseColor("#0a0a1a")
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
                p.color = Color.CYAN
                canvas.drawCircle(width * 0.5f, height * 0.5f, width * 0.36f, p)
                tp.color = Color.BLACK
                tp.typeface = Typeface.DEFAULT_BOLD
                tp.textSize = width * 0.32f
                canvas.drawText("SGS", width * 0.5f, height * 0.62f, tp)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                rightMargin = dp(8)
            }
            background = makeBoxDrawable("#0a0a1a", "#ffffff", 6f, 1)
        }
        playerBox.addView(avatarView)

        val idColumn = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
        }
        playerIdText = TextView(this).apply {
            setTextColor(Color.CYAN)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
        }
        unlockInfoText = TextView(this).apply {
            setTextColor(Color.parseColor("#aaaaaa"))
            textSize = 9f
        }
        idColumn.addView(playerIdText)
        idColumn.addView(unlockInfoText)
        playerBox.addView(idColumn)

        curLvBadgeText = TextView(this).apply {
            setTextColor(Color.BLACK)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = makeBoxDrawable("#00ffff", "#00ffff", 12f, 0)
        }
        playerBox.addView(curLvBadgeText)
        mainColumn.addView(playerBox)

        val gameWrap = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(360), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
            orientation = LinearLayout.VERTICAL
        }

        val hudBar = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)).apply {
                bottomMargin = dp(2)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            background = makeBoxDrawable("#11111e", "#00ffff", 8f, 1)
        }

        coinTopText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(Color.CYAN)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
        }
        scoreLvTopText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f)
            setTextColor(Color.parseColor("#ffeb3b"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        speedTopText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(Color.CYAN)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
        }
        soundTopText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(8)
            }
            setTextColor(Color.parseColor("#00ff88"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { toggleSound() }
        }

        hudBar.addView(coinTopText)
        hudBar.addView(scoreLvTopText)
        hudBar.addView(speedTopText)
        hudBar.addView(soundTopText)
        gameWrap.addView(hudBar)

        val canvasFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180))
            background = makeBoxDrawable("#000000", "#00ffff", 12f, 2)
            clipToOutline = true
        }
        gameView = GameView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            onHudChanged = { sc, spd, lv ->
                runOnUiThread {
                    scoreLvTopText.text = "SC $sc | Lv $lv"
                    speedTopText.text = "SPD ${spd}ms"
                }
            }
            onCoinsEarned = { amount, _ ->
                runOnUiThread {
                    updateCoins(coins + amount)
                }
            }
            onGameOver = { finalScore, _ ->
                runOnUiThread {
                    if (finalScore > highScore) {
                        highScore = finalScore
                        prefs.edit().putInt("h", highScore).apply()
                    }
                    syncAllUi()
                }
            }
            onBoardTap = {
                runOnUiThread {
                    gameView.togglePauseMatch()
                }
            }
        }
        canvasFrame.addView(gameView)
        gameWrap.addView(canvasFrame)
        mainColumn.addView(gameWrap)

        val dpadGrid = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val rowTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }
        rowTop.addView(makeControlButton("↑", isOk = false) { gameView.steer(GameView.Direction.UP) })
        dpadGrid.addView(rowTop)

        val rowMid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }
        rowMid.addView(makeControlButton("←", isOk = false) { gameView.steer(GameView.Direction.LEFT) })
        val okBtn = makeControlButton("OK", isOk = true) { gameView.togglePauseMatch() }.apply {
            (layoutParams as LinearLayout.LayoutParams).apply {
                leftMargin = dp(6)
                rightMargin = dp(6)
            }
        }
        rowMid.addView(okBtn)
        rowMid.addView(makeControlButton("→", isOk = false) { gameView.steer(GameView.Direction.RIGHT) })
        dpadGrid.addView(rowMid)

        val rowBot = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        rowBot.addView(makeControlButton("↓", isOk = false) { gameView.steer(GameView.Direction.DOWN) })
        dpadGrid.addView(rowBot)
        mainColumn.addView(dpadGrid)

        drawerLayout.addView(mainColumn)
        drawerLayout.addView(buildLeftDrawer())
        drawerLayout.addView(buildRightDrawer())
        hostFrame.addView(drawerLayout)

        panelContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.argb(245, 0, 0, 0))
            visibility = View.GONE
            isClickable = true
            isFocusable = true
        }
        hostFrame.addView(panelContainer)

        loadingOverlay = buildLoadingOverlay()
        hostFrame.addView(loadingOverlay)

        return hostFrame
    }

    private fun buildLeftDrawer(): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = DrawerLayout.LayoutParams(dp(180), DrawerLayout.LayoutParams.MATCH_PARENT, GravityCompat.START)
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(20), dp(12), dp(20))
            setBackgroundColor(Color.parseColor("#0c0c16"))

            addView(TextView(this@MainActivity).apply {
                text = "LEFT MENU"
                setTextColor(Color.CYAN)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(12))
            })

            addView(makeDrawerBtn("SHOP UNIQUE", "#15151f", "#00ffff") {
                drawerLayout.closeDrawers()
                openShopPanel(0)
            })
            addView(makeDrawerBtn("LEVELS LOCK", "#15151f", "#00ffff") {
                drawerLayout.closeDrawers()
                openLevelsPanel()
            })
            addView(makeDrawerBtn("SPIN WHEEL", "#15151f", "#00ff88") {
                drawerLayout.closeDrawers()
                openSpinWheelPanel()
            })
            addView(makeDrawerBtn("EXIT APP", "#ff4444", "#ffffff") {
                drawerLayout.closeDrawers()
                adManager.setTopBannerVisible(true)
                showExitConfirmDialog()
            })
        }
    }

    private fun buildRightDrawer(): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = DrawerLayout.LayoutParams(dp(180), DrawerLayout.LayoutParams.MATCH_PARENT, GravityCompat.END)
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(20), dp(12), dp(20))
            setBackgroundColor(Color.parseColor("#0c0c16"))

            addView(TextView(this@MainActivity).apply {
                text = "RIGHT MENU"
                setTextColor(Color.parseColor("#00ff88"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(12))
            })

            addView(makeDrawerBtn("SETTINGS", "#00ff88", "#000000") {
                drawerLayout.closeDrawers()
                openSettingsPanel()
            })
            addView(makeDrawerBtn("SOUND ON/OFF", "#15151f", "#00ffff") {
                drawerLayout.closeDrawers()
                adManager.setTopBannerVisible(true)
                toggleSound()
            })
            addView(makeDrawerBtn("OK PAUSE/PLAY", "#00ffff", "#000000") {
                drawerLayout.closeDrawers()
                adManager.setTopBannerVisible(true)
                gameView.togglePauseMatch()
            })
        }
    }

    private fun showExitConfirmDialog() {
        gameView.pauseMatch()
        AlertDialog.Builder(this)
            .setTitle("EXIT ${GameData.GAME_NAME.uppercase()}")
            .setMessage("Are you sure you want to exit the game?")
            .setPositiveButton("YES") { _, _ ->
                finishAffinity()
            }
            .setNegativeButton("NO") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun buildLoadingOverlay(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            visibility = View.VISIBLE
            isClickable = true
            isFocusable = true
        }

        val centerCol = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        val logoBox = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(84), dp(84)).apply {
                bottomMargin = dp(18)
            }
            text = "SGS"
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#00ff88"), Color.CYAN)
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(2), Color.WHITE)
            }
        }
        centerCol.addView(logoBox)

        val stepsCol = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(270), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
            orientation = LinearLayout.VERTICAL
        }

        val initialLabels = listOf(
            "☐ Engine Starting...",
            "☐ Levels Checking...",
            "☐ Skins Checking...",
            "☐ Coins & Server Sync...",
            "☐ Player Ready..."
        )
        stepViews = initialLabels.map { label ->
            TextView(this).apply {
                text = label
                setTextColor(Color.parseColor("#666666"))
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(3), 0, dp(3))
                stepsCol.addView(this)
            }
        }
        centerCol.addView(stepsCol)

        val barBg = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(260), dp(8)).apply {
                bottomMargin = dp(6)
            }
            background = makeBoxDrawable("#1a1a1a", "#1a1a1a", 10f, 0)
            clipToOutline = true
        }
        progressBarFill = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#00ff88"))
        }
        barBg.addView(progressBarFill)
        centerCol.addView(barBg)

        loadPercentText = TextView(this).apply {
            text = "0%"
            setTextColor(Color.parseColor("#00ff88"))
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
        }
        centerCol.addView(loadPercentText)

        tapToEnterBtn = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)).apply {
                topMargin = dp(16)
            }
            text = "TAP TO ENTER - SGS"
            setTextColor(Color.BLACK)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(24), 0, dp(24), 0)
            background = makeBoxDrawable("#00ff88", "#00ff88", 20f, 0)
            visibility = View.GONE
            setOnClickListener { finishLoadingAndEnterGame() }
        }
        centerCol.addView(tapToEnterBtn)

        overlay.addView(centerCol)
        return overlay
    }

    private fun startFiveStepLoading() {
        loadProgress = 0
        loadingFinished = false
        tapToEnterBtn.visibility = View.GONE

        val completedSteps = listOf(
            "☑ Engine Hissing Active - SGS Core",
            "☑ 15 Levels OK - 15 Snakes Found",
            "☑ Skins OK - Food Body Head Ready",
            "☑ Coins Encrypted & Server Secure",
            "☑ Player Ready - Snake Changer SGS"
        )
        val thresholds = listOf(5, 28, 48, 70, 88)

        val tick = object : Runnable {
            override fun run() {
                if (loadingFinished) return
                loadProgress = (loadProgress + 2).coerceAtMost(100)
                loadPercentText.text = "$loadProgress%"

                val maxFillWidth = dp(260)
                progressBarFill.layoutParams = FrameLayout.LayoutParams(
                    (maxFillWidth * loadProgress) / 100,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                for (i in thresholds.indices) {
                    if (loadProgress >= thresholds[i]) {
                        stepViews[i].text = completedSteps[i]
                        stepViews[i].setTextColor(Color.parseColor("#00ff88"))
                    }
                }

                if (loadProgress >= 100) {
                    tapToEnterBtn.visibility = View.VISIBLE
                    uiHandler.postDelayed({
                        if (!loadingFinished) finishLoadingAndEnterGame()
                    }, 900)
                } else {
                    loadingTimer = this
                    uiHandler.postDelayed(this, 60)
                }
            }
        }
        loadingTimer = tick
        uiHandler.postDelayed(tick, 60)
    }

    private fun finishLoadingAndEnterGame() {
        if (loadingFinished) return
        loadingFinished = true
        loadingTimer?.let { uiHandler.removeCallbacks(it) }
        loadingOverlay.visibility = View.GONE
        adManager.setTopBannerVisible(true)
        syncAllUi()
        gameView.startNewMatch(autoBegin = false)
    }

    private fun openSettingsPanel() {
        gameView.pauseMatch()
        showPanel(title = "SETTINGS & PRIVACY POLICY - ${GameData.GAME_NAME}") { body ->
            body.addView(makeInfoCard(
                title = "${GameData.GAME_NAME.uppercase()} - ${GameData.BRAND}",
                lines = listOf(
                    "ID: $playerId",
                    "Lv: $currentLevel | Coin: $coins | Best: $highScore"
                ),
                borderHex = "#00ffff",
                titleHex = "#00ffff"
            ))

            val rateCard = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(10)
                }
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = makeBoxDrawable("#111111", "#ffeb3b", 12f, 2)
            }
            rateCard.addView(TextView(this).apply {
                text = "Rating 3+"
                setTextColor(Color.parseColor("#ffeb3b"))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
            })
            rateCard.addView(TextView(this).apply {
                text = "Tap to Rate & Write Message (+${GameData.RATE_BONUS_COINS} Bonus Coins)"
                setTextColor(Color.parseColor("#aaaaaa"))
                textSize = 10f
                setPadding(0, dp(4), 0, dp(8))
            })
            rateCard.addView(Button(this).apply {
                text = "RATE US"
                setTextColor(Color.BLACK)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(20), dp(6), dp(20), dp(6))
                background = makeBoxDrawable("#ffeb3b", "#ffeb3b", 20f, 0)
                setOnClickListener {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GameData.UPTODOWN_URL)))
                    } catch (_: Exception) {
                    }
                    if (!prefs.getBoolean("rated", false)) {
                        prefs.edit().putBoolean("rated", true).apply()
                        updateCoins(coins + GameData.RATE_BONUS_COINS)
                        showSimpleDialog("Rating Recorded", "+${GameData.RATE_BONUS_COINS} Bonus Coins Added!")
                    }
                }
            })
            body.addView(rateCard)

            body.addView(makeToggleRow("Sound Effects", soundOn) {
                toggleSound()
                openSettingsPanel()
            })
            body.addView(makeToggleRow("Vibration", vibOn) {
                vibOn = !vibOn
                prefs.edit().putBoolean("vibOn", vibOn).apply()
                gameView.configureSettings(soundOn, vibOn, trailOn)
                openSettingsPanel()
            })
            body.addView(makeToggleRow("Trail Effect", trailOn) {
                trailOn = !trailOn
                prefs.edit().putBoolean("trailOn", trailOn).apply()
                gameView.configureSettings(soundOn, vibOn, trailOn)
                openSettingsPanel()
            })

            val resetRow = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4)
                    bottomMargin = dp(10)
                }
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = makeBoxDrawable("#111111", "#222222", 10f, 1)
            }
            resetRow.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = "Secure Data Reset (Clears Score Only)"
                setTextColor(Color.WHITE)
                textSize = 11f
            })
            resetRow.addView(Button(this).apply {
                text = "SECURE RESET"
                setTextColor(Color.WHITE)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = makeBoxDrawable("#ff4444", "#ff4444", 18f, 0)
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Secure Reset")
                        .setMessage("Reset your high score only? Coins, Levels & Shop items stay safe.")
                        .setPositiveButton("YES") { _, _ ->
                            highScore = 0
                            prefs.edit().putInt("h", 0).apply()
                            syncAllUi()
                            openSettingsPanel()
                        }
                        .setNegativeButton("NO", null)
                        .show()
                }
            })
            body.addView(resetRow)

            body.addView(makeInfoCard(
                title = "Game Info & Official Privacy Policy",
                lines = listOf(
                    "• Game Name: ${GameData.GAME_NAME} (${GameData.BRAND} Edition)",
                    "• Studio: ${GameData.STUDIO} | Developer: ${GameData.DEVELOPER}",
                    "• Content Rating: Everyone 3+",
                    "• Privacy Policy: Coins, high-scores, and progress are saved securely in local storage."
                ),
                borderHex = "#00ff88",
                titleHex = "#00ff88"
            ))
        }
    }

    private fun openLevelsPanel() {
        gameView.pauseMatch()
        showPanel(title = "LEVELS - 15 Levels 15 Snakes") { body ->
            for (lv in 1..15) {
                val cfg = GameData.safeLevel(lv)
                val fullModel = GameData.safeFull(cfg.full)
                val cost = GameData.levelCosts[lv - 1]
                val unlocked = isLevelUnlocked(lv)
                val isCurrent = currentLevel == lv

                val borderHex = when {
                    isCurrent -> "#00ffff"
                    unlocked -> "#00ff88"
                    else -> "#ff4444"
                }

                val card = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(8)
                    }
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = makeBoxDrawable("#15151f", borderHex, 12f, 2)
                }

                val infoCol = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    orientation = LinearLayout.VERTICAL
                }
                val costLabel = if (cost == 0) "FREE" else "${cost} Coin"
                infoCol.addView(TextView(this).apply {
                    text = "Lv$lv ${fullModel.name} - $costLabel"
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                })
                infoCol.addView(TextView(this).apply {
                    text = if (unlocked) "UNLOCKED - 15 Snakes Unique" else "LOCK ${cost} Coin"
                    setTextColor(if (unlocked) Color.parseColor("#00ff88") else Color.parseColor("#ff4444"))
                    textSize = 9f
                })
                card.addView(infoCol)

                val actionBtn = Button(this).apply {
                    text = if (unlocked) "PLAY" else "UNLOCK $cost"
                    setTextColor(if (unlocked) Color.BLACK else Color.WHITE)
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    background = makeBoxDrawable(
                        if (unlocked) "#ffeb3b" else "#444444",
                        if (unlocked) "#ffeb3b" else "#444444",
                        18f,
                        0
                    )
                    setOnClickListener {
                        if (unlocked) {
                            currentLevel = lv
                            prefs.edit().putInt("curLv", currentLevel).apply()
                            syncAllUi()
                            closePanel()
                            gameView.startNewMatch(autoBegin = true)
                        } else if (coins >= cost) {
                            updateCoins(coins - cost)
                            prefs.edit()
                                .putBoolean("lvUnlock_$lv", true)
                                .putInt("curLv", lv)
                                .apply()
                            currentLevel = lv
                            syncAllUi()
                            closePanel()
                            gameView.startNewMatch(autoBegin = true)
                        } else {
                            showSimpleDialog("Not Enough Coins", "Need $cost Coins. You have $coins Coins.")
                        }
                    }
                }
                card.addView(actionBtn)
                body.addView(card)
            }
        }
    }

    private fun openSpinWheelPanel() {
        gameView.pauseMatch()
        showPanel(title = "SPIN WHEEL - Reward 1500/2000/3000 Coins") { body ->
            val wrap = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(12), dp(18), dp(12), dp(18))
            }

            val wheelView = object : View(this) {
                var angleDeg = 0f
                private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
                private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                }
                private val colors = listOf(
                    Color.CYAN,
                    Color.MAGENTA,
                    Color.WHITE,
                    Color.YELLOW,
                    Color.parseColor("#00ff88"),
                    Color.parseColor("#ff9900")
                )
                private val labels = listOf("1500", "2000", "3000", "1500", "2000", "3000")

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val cx = width * 0.5f
                    val cy = height * 0.5f
                    val radius = minOf(width, height) * 0.45f

                    canvas.save()
                    canvas.rotate(angleDeg, cx, cy)
                    for (i in 0 until 6) {
                        slicePaint.color = colors[i]
                        canvas.drawArc(
                            cx - radius, cy - radius, cx + radius, cy + radius,
                            i * 60f, 60f, true, slicePaint
                        )
                        val midRad = Math.toRadians((i * 60 + 30).toDouble())
                        val lx = cx + (Math.cos(midRad) * radius * 0.62f).toFloat()
                        val ly = cy + (Math.sin(midRad) * radius * 0.62f).toFloat()
                        labelPaint.color = Color.BLACK
                        labelPaint.textSize = 24f
                        canvas.drawText(labels[i], lx, ly + 8f, labelPaint)
                    }
                    canvas.restore()

                    slicePaint.color = Color.BLACK
                    canvas.drawCircle(cx, cy, radius * 0.22f, slicePaint)
                    labelPaint.color = Color.CYAN
                    labelPaint.textSize = 22f
                    canvas.drawText("SGS", cx, cy + 7f, labelPaint)
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(dp(200), dp(200)).apply {
                    bottomMargin = dp(16)
                }
            }
            wrap.addView(wheelView)

            val resultText = TextView(this).apply {
                text = "Current Coins: $coins"
                setTextColor(Color.parseColor("#00ff88"))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(12))
            }
            wrap.addView(resultText)

            val spinBtn = Button(this).apply {
                text = "SPIN WHEEL (+1500 / 2000 / 3000)"
                setTextColor(Color.BLACK)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(20), dp(10), dp(20), dp(10))
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.CYAN, Color.MAGENTA)
                ).apply { cornerRadius = dp(24).toFloat() }
            }

            spinBtn.setOnClickListener {
                spinBtn.isEnabled = false
                resultText.text = "Spinning..."
                adManager.showSpinRewardedAd(
                    onRewardGranted = {
                        val reward = GameData.spinRewards[Random.nextInt(GameData.spinRewards.size)]
                        val startAngle = wheelView.angleDeg % 360f
                        val targetAngle = startAngle + 1440f + Random.nextInt(360).toFloat()
                        wheelAnimator?.cancel()
                        wheelAnimator = ValueAnimator.ofFloat(startAngle, targetAngle).apply {
                            duration = 2200L
                            interpolator = DecelerateInterpolator()
                            addUpdateListener { anim ->
                                wheelView.angleDeg = anim.animatedValue as Float
                                wheelView.invalidate()
                            }
                            addListener(object : android.animation.AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: android.animation.Animator) {
                                    updateCoins(coins + reward)
                                    resultText.text = "Won +$reward Coins! Total: $coins"
                                    spinBtn.isEnabled = true
                                }
                            })
                            start()
                        }
                    }
                )
            }
            wrap.addView(spinBtn)
            body.addView(wrap)
        }
    }

    private fun openShopPanel(activeTab: Int) {
        gameView.pauseMatch()
        showPanel(title = "SHOP UNIQUE - 4 Types") { body ->
            val tabBar = HorizontalScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(10)
                }
            }
            val tabRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val tabNames = listOf("Food Shape", "Body Pattern", "Full Compact", "Head Char")
            tabNames.forEachIndexed { index, label ->
                val selected = index == activeTab
                tabRow.addView(Button(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply {
                        rightMargin = dp(6)
                    }
                    text = label
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (selected) Color.BLACK else Color.WHITE)
                    setPadding(dp(12), 0, dp(12), 0)
                    background = makeBoxDrawable(
                        if (selected) "#00ffff" else "#111111",
                        if (selected) "#00ffff" else "#333333",
                        18f,
                        1
                    )
                    setOnClickListener { openShopPanel(index) }
                })
            }
            tabBar.addView(tabRow)
            body.addView(tabBar)

            when (activeTab) {
                0 -> GameData.foodItems.forEachIndexed { idx, item ->
                    val unlocked = isItemUnlocked("fP", idx) || idx == 0
                    val equipped = selectedFood == idx
                    body.addView(makeShopRow(
                        title = "${item.name} - ${item.shape} - ${item.price} Coin",
                        colorHex = item.colorHex,
                        unlocked = unlocked,
                        equipped = equipped,
                        price = item.price
                    ) {
                        if (unlocked) {
                            selectedFood = idx
                            prefs.edit().putInt("sel_food", idx).apply()
                            syncAllUi()
                            openShopPanel(0)
                        } else if (coins >= item.price) {
                            updateCoins(coins - item.price)
                            unlockItem("fP", idx)
                            selectedFood = idx
                            prefs.edit().putInt("sel_food", idx).apply()
                            syncAllUi()
                            openShopPanel(0)
                        } else {
                            showSimpleDialog("Not Enough Coins", "Need ${item.price} Coins. You have $coins Coins.")
                        }
                    })
                }

                1 -> GameData.bodyItems.forEachIndexed { idx, item ->
                    val unlocked = isItemUnlocked("bP", idx) || idx == 0
                    val equipped = selectedBody == idx
                    body.addView(makeShopRow(
                        title = "${item.name} - ${item.pattern} - ${item.price} Coin",
                        colorHex = item.colorHex,
                        unlocked = unlocked,
                        equipped = equipped,
                        price = item.price
                    ) {
                        if (unlocked) {
                            selectedBody = idx
                            prefs.edit().putInt("sel_body", idx).apply()
                            syncAllUi()
                            openShopPanel(1)
                        } else if (coins >= item.price) {
                            updateCoins(coins - item.price)
                            unlockItem("bP", idx)
                            selectedBody = idx
                            prefs.edit().putInt("sel_body", idx).apply()
                            syncAllUi()
                            openShopPanel(1)
                        } else {
                            showSimpleDialog("Not Enough Coins", "Need ${item.price} Coins. You have $coins Coins.")
                        }
                    })
                }

                2 -> GameData.fullBodies.forEachIndexed { idx, item ->
                    val unlocked = isItemUnlocked("fbP", idx) || idx == 0
                    val equipped = selectedFull == idx
                    body.addView(makeShopRow(
                        title = "${item.name} - ${item.pattern} - ${item.price} Coin",
                        colorHex = item.dotHex,
                        unlocked = unlocked,
                        equipped = equipped,
                        price = item.price
                    ) {
                        if (unlocked) {
                            selectedFull = idx
                            prefs.edit().putInt("sel_full", idx).apply()
                            syncAllUi()
                            openShopPanel(2)
                        } else if (coins >= item.price) {
                            updateCoins(coins - item.price)
                            unlockItem("fbP", idx)
                            selectedFull = idx
                            prefs.edit().putInt("sel_full", idx).apply()
                            syncAllUi()
                            openShopPanel(2)
                        } else {
                            showSimpleDialog("Not Enough Coins", "Need ${item.price} Coins. You have $coins Coins.")
                        }
                    })
                }

                else -> GameData.heads.forEachIndexed { idx, item ->
                    val unlocked = isItemUnlocked("hP", idx) || idx == 0
                    val equipped = selectedHead == idx
                    body.addView(makeShopRow(
                        title = "Head ${item.name} ${item.emoji} - ${item.price} Coin",
                        colorHex = item.colorHex,
                        unlocked = unlocked,
                        equipped = equipped,
                        price = item.price
                    ) {
                        if (unlocked) {
                            selectedHead = idx
                            prefs.edit().putInt("sel_head", idx).apply()
                            syncAllUi()
                            openShopPanel(3)
                        } else if (coins >= item.price) {
                            updateCoins(coins - item.price)
                            unlockItem("hP", idx)
                            selectedHead = idx
                            prefs.edit().putInt("sel_head", idx).apply()
                            syncAllUi()
                            openShopPanel(3)
                        } else {
                            showSimpleDialog("Not Enough Coins", "Need ${item.price} Coins. You have $coins Coins.")
                        }
                    })
                }
            }
        }
    }

    private fun openProfilePanel() {
        gameView.pauseMatch()
        showPanel(title = "PLAYER PROFILE & SGS ID") { body ->
            body.addView(makeInfoCard(
                title = "SGS PLAYER ID: $playerId",
                lines = listOf(
                    "Current Level: Lv $currentLevel / 15",
                    "Coins: $coins | High Score: $highScore",
                    "Status: Secure ID - Locked & Protected"
                ),
                borderHex = "#00ffff",
                titleHex = "#00ffff"
            ))
        }
    }

    private fun showPanel(title: String, populate: (LinearLayout) -> Unit) {
        adManager.setTopBannerVisible(false)
        panelContainer.removeAllViews()
        panelContainer.visibility = View.VISIBLE

        val sheet = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(64), 0, 0)
            setBackgroundColor(Color.parseColor("#080810"))
        }

        val header = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.BLACK)
        }
        header.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = title
            setTextColor(Color.CYAN)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        })
        header.addView(Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            text = "X"
            setTextColor(Color.BLACK)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 0)
            background = makeBoxDrawable("#00ffff", "#ffffff", 18f, 2)
            setOnClickListener { closePanel() }
        })
        sheet.addView(header)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setPadding(dp(12), dp(10), dp(12), dp(16))
        }
        val contentCol = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            orientation = LinearLayout.VERTICAL
        }
        populate(contentCol)
        scroll.addView(contentCol)
        sheet.addView(scroll)

        panelContainer.addView(sheet)
    }

    private fun closePanel() {
        wheelAnimator?.cancel()
        panelContainer.visibility = View.GONE
        panelContainer.removeAllViews()
        adManager.setTopBannerVisible(true)
        gameView.invalidate()
    }

    private fun toggleSound() {
        soundOn = !soundOn
        prefs.edit().putBoolean("soundOn", soundOn).apply()
        gameView.configureSettings(soundOn, vibOn, trailOn)
        syncAllUi()
    }

    private fun syncAllUi() {
        val maxLv = highestUnlockedLevel()
        playerIdText.text = "ID: $playerId"
        unlockInfoText.text = "Unlock: Lv $maxLv/15 | Left: ${15 - maxLv}"
        curLvBadgeText.text = "Lv $currentLevel"

        coinTopText.text = "COIN $coins"
        scoreLvTopText.text = "SC ${gameView.getCurrentScore()} | Lv $currentLevel"
        speedTopText.text = "SPD ${gameView.getSpeedMs()}ms"
        soundTopText.text = if (soundOn) "SOUND" else "MUTE"

        gameView.configureSettings(soundOn, vibOn, trailOn)
        gameView.applyTheme(currentLevel, selectedFood, selectedBody, selectedFull, selectedHead)
    }

    private fun showSimpleDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (panelContainer.visibility == View.VISIBLE) {
            closePanel()
            return
        }
        if (drawerLayout.isDrawerOpen(GravityCompat.START) || drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawers()
            adManager.setTopBannerVisible(true)
            return
        }
        showExitConfirmDialog()
    }

    override fun onPause() {
        super.onPause()
        gameView.pauseMatch()
    }

    override fun onDestroy() {
        super.onDestroy()
        wheelAnimator?.cancel()
        loadingTimer?.let { uiHandler.removeCallbacks(it) }
        gameView.stopLoop()
        adManager.destroy()
    }

    private fun makeControlButton(label: String, isOk: Boolean, onClick: () -> Unit): Button {
        return Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(46))
            text = label
            textSize = if (isOk) 11f else 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isOk) Color.BLACK else Color.CYAN)
            setPadding(0, 0, 0, 0)
            background = makeBoxDrawable(
                if (isOk) "#00ffff" else "#15151f",
                if (isOk) "#00ffff" else "#6600ffff",
                14f,
                2
            )
            setOnClickListener { onClick() }
        }
    }

    private fun makeSmallActionBtn(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28))
            text = label
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.CYAN)
            setPadding(dp(10), 0, dp(10), 0)
            background = makeBoxDrawable("#15151f", "#6600ffff", 8f, 1)
            setOnClickListener { onClick() }
        }
    }

    private fun makeDrawerBtn(label: String, bgHex: String, textHex: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply {
                bottomMargin = dp(8)
            }
            text = label
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(textHex))
            background = makeBoxDrawable(bgHex, "#4400ffff", 9f, 1)
            setOnClickListener { onClick() }
        }
    }

    private fun makeToggleRow(label: String, enabled: Boolean, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = makeBoxDrawable("#111111", "#222222", 10f, 1)

            addView(TextView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = label
                setTextColor(Color.WHITE)
                textSize = 11f
            })
            addView(Button(this@MainActivity).apply {
                text = if (enabled) "ON" else "OFF"
                setTextColor(if (enabled) Color.BLACK else Color.WHITE)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(14), dp(6), dp(14), dp(6))
                background = makeBoxDrawable(
                    if (enabled) "#ffeb3b" else "#444444",
                    if (enabled) "#ffeb3b" else "#444444",
                    18f,
                    0
                )
                setOnClickListener { onClick() }
            })
        }
    }

    private fun makeShopRow(
        title: String,
        colorHex: String,
        unlocked: Boolean,
        equipped: Boolean,
        price: Int,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(7)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#15151f"), Color.parseColor("#1a1a2e"))
            ).apply {
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor(if (equipped) "#00ff88" else "#222222"))
            }

            val dot = View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply {
                    rightMargin = dp(8)
                }
                background = makeBoxDrawable(colorHex, "#ffffff", 6f, 1)
            }
            addView(dot)

            addView(TextView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = title
                setTextColor(Color.WHITE)
                textSize = 10f
            })

            val btnLabel = when {
                equipped -> "EQUIPPED"
                unlocked -> "USE"
                else -> "$price COIN"
            }
            val btnBg = when {
                equipped -> "#00ff88"
                unlocked -> "#ffeb3b"
                else -> "#00ffff"
            }

            addView(Button(this@MainActivity).apply {
                text = btnLabel
                setTextColor(Color.BLACK)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = makeBoxDrawable(btnBg, btnBg, 18f, 0)
                setOnClickListener { onClick() }
            })
        }
    }

    private fun makeInfoCard(
        title: String,
        lines: List<String>,
        borderHex: String,
        titleHex: String
    ): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = makeBoxDrawable("#111111", borderHex, 12f, 2)

            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(Color.parseColor(titleHex))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(4))
            })
            lines.forEach { line ->
                addView(TextView(this@MainActivity).apply {
                    text = line
                    setTextColor(Color.parseColor("#cccccc"))
                    textSize = 10f
                    setPadding(0, dp(2), 0, dp(2))
                })
            }
        }
    }

    private fun makeBoxDrawable(fillHex: String, strokeHex: String, radiusDp: Float, strokeDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            try {
                setColor(Color.parseColor(fillHex))
            } catch (_: Exception) {
                setColor(Color.BLACK)
            }
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            if (strokeDp > 0) {
                try {
                    setStroke(dp(strokeDp), Color.parseColor(strokeHex))
                } catch (_: Exception) {
                    setStroke(dp(strokeDp), Color.CYAN)
                }
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
