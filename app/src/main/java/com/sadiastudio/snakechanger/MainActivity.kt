package com.sadiastudio.snakechanger

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("sgs_snake_changer_prefs", Context.MODE_PRIVATE)
    }

    private val uiHandler = Handler(Looper.getMainLooper())

    private var coins = GameData.START_COINS
    private var unlockedLevel = 1
    private var curLv = 1
    private var foodIdx = 0
    private var bodyIdx = 0
    private var fullIdx = 0
    private var headIdx = 0

    private var unlockedFoods = mutableSetOf(0)
    private var unlockedBodies = mutableSetOf(0)
    private var unlockedFulls = mutableSetOf(0)
    private var unlockedHeads = mutableSetOf(0)

    private var soundOn = true
    private var vibOn = true
    private var trailOn = true
    private var reminderOn = true
    private var ratedBonusClaimed = false
    private var showModelBadge = true
    private var modelPresetMode = "AUTO"

    private var activeTab = "LEVELS"
    private var shopCategory = "FOOD"
    private var menuVisible = false
    private var settingsVisible = false

    private lateinit var rootLayout: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var tvCoin: TextView
    private lateinit var tvBest: TextView
    private lateinit var tvScore: TextView
    private lateinit var tvLevelInfo: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var btnMenuToggle: Button
    private lateinit var btnSettingsGear: Button

    private lateinit var middleArea: FrameLayout
    private lateinit var gameView: GameView
    private lateinit var overlayPanel: LinearLayout
    private lateinit var settingsPanel: LinearLayout

    private lateinit var bottomArea: LinearLayout
    private lateinit var dpadContainer: LinearLayout
    private lateinit var bannerHost: FrameLayout
    private var bannerWebView: WebView? = null

    private var toastBanner: TextView? = null
    private var hideToastRunnable: Runnable? = null
    private var adManager: AdManager? = null
    private var isSpinningAd = false
    private var isFinishingExit = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NotificationHelper.setReminderEnabled(this, true)
            reminderOn = true
            showToast("24h reminder enabled")
        } else {
            showToast("Permission denied; reminder stays active once allowed")
        }
        refreshSettingsContent()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        loadSavedState()

        adManager = AdManager(this)
        NotificationHelper.createChannel(this)
        if (reminderOn) {
            NotificationHelper.scheduleDailyReminder(this)
        } else {
            NotificationHelper.cancelDailyReminder(this)
        }

        buildUi()
        syncGameViewThemeAndSettings()
        updateHud(
            score = gameView.getCurrentScore(),
            bestScore = gameView.getLevelBestScore(curLv),
            speedMs = gameView.getSpeedMs(),
            level = curLv
        )
        setupBannerAdSafe()
    }

    private fun enableImmersiveMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
            }
        } catch (_: Exception) {
        }
    }

    private fun loadSavedState() {
        coins = GameData.decodeCoins(prefs.getString("pc_wallet_enc", null))
        unlockedLevel = prefs.getInt("unlocked_level", 1).coerceIn(1, 15)
        curLv = prefs.getInt("cur_lv", 1).coerceIn(1, unlockedLevel)

        foodIdx = prefs.getInt("food_idx", 0).coerceIn(0, GameData.foodItems.lastIndex)
        bodyIdx = prefs.getInt("body_idx", 0).coerceIn(0, GameData.bodyItems.lastIndex)
        fullIdx = prefs.getInt("full_idx", 0).coerceIn(0, GameData.fullBodies.lastIndex)
        headIdx = prefs.getInt("head_idx", 0).coerceIn(0, GameData.heads.lastIndex)

        unlockedFoods = parseIndexSet(prefs.getString("unlocked_foods", "0"), GameData.foodItems.lastIndex)
        unlockedBodies = parseIndexSet(prefs.getString("unlocked_bodies", "0"), GameData.bodyItems.lastIndex)
        unlockedFulls = parseIndexSet(prefs.getString("unlocked_fulls", "0"), GameData.fullBodies.lastIndex)
        unlockedHeads = parseIndexSet(prefs.getString("unlocked_heads", "0"), GameData.heads.lastIndex)

        soundOn = prefs.getBoolean("sound_on", true)
        vibOn = prefs.getBoolean("vib_on", true)
        trailOn = prefs.getBoolean("trail_on", true)
        reminderOn = NotificationHelper.isReminderEnabled(this)
        ratedBonusClaimed = prefs.getBoolean("rated_bonus_claimed", false)
        showModelBadge = prefs.getBoolean("show_model_badge", true)
        modelPresetMode = prefs.getString("model_preset_mode", "AUTO") ?: "AUTO"
    }

    private fun parseIndexSet(raw: String?, maxIndex: Int): MutableSet<Int> {
        val set = mutableSetOf(0)
        if (!raw.isNullOrBlank()) {
            raw.split(",").forEach { part ->
                part.trim().toIntOrNull()?.let { idx ->
                    if (idx in 0..maxIndex) set.add(idx)
                }
            }
        }
        return set
    }

    private fun saveState() {
        prefs.edit()
            .putString("pc_wallet_enc", GameData.encodeCoins(coins))
            .putInt("unlocked_level", unlockedLevel)
            .putInt("cur_lv", curLv)
            .putInt("food_idx", foodIdx)
            .putInt("body_idx", bodyIdx)
            .putInt("full_idx", fullIdx)
            .putInt("head_idx", headIdx)
            .putString("unlocked_foods", unlockedFoods.joinToString(","))
            .putString("unlocked_bodies", unlockedBodies.joinToString(","))
            .putString("unlocked_fulls", unlockedFulls.joinToString(","))
            .putString("unlocked_heads", unlockedHeads.joinToString(","))
            .putBoolean("sound_on", soundOn)
            .putBoolean("vib_on", vibOn)
            .putBoolean("trail_on", trailOn)
            .putBoolean("rated_bonus_claimed", ratedBonusClaimed)
            .putBoolean("show_model_badge", showModelBadge)
            .putString("model_preset_mode", modelPresetMode)
            .apply()
    }

    private fun buildUi() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#07090f"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#101622"))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        tvCoin = makeStatChip("#ffbe0b")
        tvBest = makeStatChip("#00f5d4")
        tvScore = makeStatChip("#00ff88")
        tvSpeed = makeStatChip("#ff85a1")

        btnSettingsGear = Button(this).apply {
            text = "⚙"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = makeRoundedBg("#1e293b", "#64748b", 10f)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            val lp = LinearLayout.LayoutParams(dp(38), dp(32)).apply {
                leftMargin = dp(4)
            }
            layoutParams = lp
            setOnClickListener { toggleSettingsPanel() }
        }

        btnMenuToggle = Button(this).apply {
            text = "MENU"
            textSize = 11f
            setTextColor(Color.parseColor("#071018"))
            setTypeface(typeface, Typeface.BOLD)
            background = makeRoundedBg("#00f5d4", "#00f5d4", 10f)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply {
                leftMargin = dp(4)
            }
            layoutParams = lp
            setOnClickListener { toggleMenuPanel() }
        }

        statsRow.addView(tvCoin, LinearLayout.LayoutParams(0, dp(32), 1.15f).apply { rightMargin = dp(4) })
        statsRow.addView(tvBest, LinearLayout.LayoutParams(0, dp(32), 1.0f).apply { rightMargin = dp(4) })
        statsRow.addView(tvScore, LinearLayout.LayoutParams(0, dp(32), 0.9f).apply { rightMargin = dp(4) })
        statsRow.addView(tvSpeed, LinearLayout.LayoutParams(0, dp(32), 0.9f))
        statsRow.addView(btnSettingsGear)
        statsRow.addView(btnMenuToggle)

        tvLevelInfo = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#cbd5e1"))
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        topBar.addView(statsRow)
        topBar.addView(tvLevelInfo)

        middleArea = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        gameView = GameView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            onHudChanged = { sc, best, spd, lv ->
                runOnUiThread { updateHud(sc, best, spd, lv) }
            }
            onPowerCoinEarned = { amount, coinName, isSpecial ->
                runOnUiThread {
                    coins += amount
                    saveState()
                    val tag = if (isSpecial) "Bonus" else "10 Food"
                    showToast("+$amount Power Coin ($coinName • $tag)")
                    updateHud(
                        score = gameView.getCurrentScore(),
                        bestScore = gameView.getLevelBestScore(curLv),
                        speedMs = gameView.getSpeedMs(),
                        level = curLv
                    )
                }
            }
            onGameOver = { finalScore, bestScore, lv ->
                runOnUiThread {
                    updateHud(finalScore, bestScore, gameView.getSpeedMs(), lv)
                    showToast("Lv$lv Over • Score $finalScore | Best $bestScore")
                }
            }
            onBoardTap = {
                if (!menuVisible && !settingsVisible) {
                    gameView.togglePauseMatch()
                }
            }
        }

        overlayPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#eb0b101b"))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        settingsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#f0090e18"))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        toastBanner = TextView(this).apply {
            visibility = View.GONE
            textSize = 11f
            setTextColor(Color.parseColor("#071018"))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = makeRoundedBg("#ffbe0b", "#fef08a", 14f)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = dp(6)
            }
            layoutParams = lp
        }

        middleArea.addView(gameView)
        middleArea.addView(overlayPanel)
        middleArea.addView(settingsPanel)
        middleArea.addView(toastBanner)

        bottomArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#101622"))
            setPadding(dp(8), dp(4), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        dpadContainer = buildCompactDpad()
        bannerHost = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#090d16"))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
            ).apply {
                topMargin = dp(4)
            }
            layoutParams = lp
        }

        bottomArea.addView(dpadContainer)
        bottomArea.addView(bannerHost)

        rootLayout.addView(topBar)
        rootLayout.addView(middleArea)
        rootLayout.addView(bottomArea)

        setContentView(rootLayout)
    }

    private fun makeStatChip(accentHex: String): TextView {
        return TextView(this).apply {
            textSize = 10f
            setTextColor(Color.parseColor(accentHex))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            background = makeRoundedBg("#182235", "#334155", 8f)
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
    }

    private fun buildCompactDpad(): LinearLayout {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val btnPause = makePadBtn("PAUSE", "#1e293b", "#94a3b8", dp(68), dp(34)).apply {
            setOnClickListener { gameView.togglePauseMatch() }
        }
        val btnRestart = makePadBtn("RESTART", "#1e293b", "#00f5d4", dp(68), dp(34)).apply {
            setOnClickListener { gameView.startNewMatch(autoBegin = true) }
        }
        leftCol.addView(btnPause, LinearLayout.LayoutParams(dp(68), dp(34)).apply { bottomMargin = dp(4) })
        leftCol.addView(btnRestart, LinearLayout.LayoutParams(dp(68), dp(34)))

        val padGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(10)
                rightMargin = dp(10)
            }
            layoutParams = lp
        }

        val rowUp = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val btnUp = makePadBtn("▲", "#17253b", "#00f5d4", dp(56), dp(32)).apply {
            setOnClickListener { gameView.steer(GameView.Direction.UP) }
        }
        rowUp.addView(btnUp)

        val rowMid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
                bottomMargin = dp(3)
            }
            layoutParams = lp
        }
        val btnLeft = makePadBtn("◀", "#17253b", "#00f5d4", dp(52), dp(32)).apply {
            setOnClickListener { gameView.steer(GameView.Direction.LEFT) }
        }
        val btnOk = makePadBtn("GO", "#00ff88", "#071018", dp(46), dp(32)).apply {
            setOnClickListener {
                if (gameView.getCurrentScore() == 0) {
                    gameView.startNewMatch(autoBegin = true)
                } else {
                    gameView.togglePauseMatch()
                }
            }
        }
        val btnRight = makePadBtn("▶", "#17253b", "#00f5d4", dp(52), dp(32)).apply {
            setOnClickListener { gameView.steer(GameView.Direction.RIGHT) }
        }
        rowMid.addView(btnLeft, LinearLayout.LayoutParams(dp(52), dp(32)).apply { rightMargin = dp(4) })
        rowMid.addView(btnOk, LinearLayout.LayoutParams(dp(46), dp(32)).apply { rightMargin = dp(4) })
        rowMid.addView(btnRight)

        val rowDown = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val btnDown = makePadBtn("▼", "#17253b", "#00f5d4", dp(56), dp(32)).apply {
            setOnClickListener { gameView.steer(GameView.Direction.DOWN) }
        }
        rowDown.addView(btnDown)

        padGrid.addView(rowUp)
        padGrid.addView(rowMid)
        padGrid.addView(rowDown)

        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val btnSpinQuick = makePadBtn("SPIN", "#3b1d06", "#ffbe0b", dp(68), dp(34)).apply {
            setOnClickListener {
                openMenuTab("SPIN")
            }
        }
        val btnShopQuick = makePadBtn("SHOP", "#1e1b4b", "#c4b5fd", dp(68), dp(34)).apply {
            setOnClickListener {
                openMenuTab("SHOP")
            }
        }
        rightCol.addView(btnSpinQuick, LinearLayout.LayoutParams(dp(68), dp(34)).apply { bottomMargin = dp(4) })
        rightCol.addView(btnShopQuick, LinearLayout.LayoutParams(dp(68), dp(34)))

        wrap.addView(leftCol)
        wrap.addView(padGrid)
        wrap.addView(rightCol)
        return wrap
    }

    private fun makePadBtn(label: String, bgHex: String, textHex: String, w: Int, h: Int): Button {
        return Button(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.parseColor(textHex))
            setTypeface(typeface, Typeface.BOLD)
            background = makeRoundedBg(bgHex, textHex, 8f)
            setPadding(dp(2), dp(2), dp(2), dp(2))
            layoutParams = LinearLayout.LayoutParams(w, h)
        }
    }

    private fun toggleMenuPanel() {
        if (menuVisible) {
            closeOverlays(resumeIfPaused = true)
        } else {
            openMenuTab(activeTab)
        }
    }

    private fun openMenuTab(tab: String) {
        activeTab = tab
        settingsVisible = false
        settingsPanel.visibility = View.GONE
        menuVisible = true
        overlayPanel.visibility = View.VISIBLE
        gameView.pauseMatch()
        refreshMenuContent()
    }

    private fun toggleSettingsPanel() {
        if (settingsVisible) {
            closeOverlays(resumeIfPaused = true)
        } else {
            menuVisible = false
            overlayPanel.visibility = View.GONE
            settingsVisible = true
            settingsPanel.visibility = View.VISIBLE
            gameView.pauseMatch()
            refreshSettingsContent()
        }
    }

    private fun closeOverlays(resumeIfPaused: Boolean) {
        menuVisible = false
        settingsVisible = false
        overlayPanel.visibility = View.GONE
        settingsPanel.visibility = View.GONE
        if (resumeIfPaused && gameView.getCurrentScore() > 0) {
            gameView.resumeMatch()
        } else {
            gameView.invalidate()
        }
    }

    private fun refreshMenuContent() {
        overlayPanel.removeAllViews()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val title = TextView(this).apply {
            text = "${GameData.GAME_NAME} • ${GameData.BRAND}"
            textSize = 13f
            setTextColor(Color.parseColor("#00f5d4"))
            setTypeface(typeface, Typeface.BOLD)
        }

        val btnClose = Button(this).apply {
            text = "PLAY"
            textSize = 11f
            setTextColor(Color.parseColor("#071018"))
            setTypeface(typeface, Typeface.BOLD)
            background = makeRoundedBg("#00ff88", "#00ff88", 8f)
            setPadding(dp(10), dp(2), dp(10), dp(2))
            setOnClickListener {
                closeOverlays(resumeIfPaused = true)
                gameView.startNewMatch(autoBegin = true)
            }
        }

        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(btnClose, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)))

        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val tabs = listOf("LEVELS", "SHOP", "SPIN", "MORE")
        for (tab in tabs) {
            val selected = activeTab == tab
            val tb = Button(this).apply {
                text = tab
                textSize = 10f
                setTextColor(if (selected) Color.parseColor("#071018") else Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                background = makeRoundedBg(
                    if (selected) "#00f5d4" else "#1a253a",
                    if (selected) "#00f5d4" else "#334155",
                    8f
                )
                setPadding(dp(4), dp(2), dp(4), dp(2))
                setOnClickListener {
                    activeTab = tab
                    refreshMenuContent()
                }
            }
            val lp = LinearLayout.LayoutParams(0, dp(30), 1f).apply {
                if (tab != tabs.last()) rightMargin = dp(4)
            }
            tabBar.addView(tb, lp)
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        when (activeTab) {
            "LEVELS" -> populateLevelsTab(body)
            "SHOP" -> populateShopTab(body)
            "SPIN" -> populateSpinTab(body)
            else -> populateMoreTab(body)
        }

        scroll.addView(body)
        overlayPanel.addView(header)
        overlayPanel.addView(tabBar)
        overlayPanel.addView(scroll)
    }

    private fun populateLevelsTab(container: LinearLayout) {
        for (i in GameData.levels.indices) {
            val lvNum = i + 1
            val cfg = GameData.levels[i]
            val cost = GameData.levelCosts.getOrElse(i) { 0 }
            val isUnlocked = lvNum <= unlockedLevel
            val isSelected = curLv == lvNum
            val lvBest = gameView.getLevelBestScore(lvNum)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = makeRoundedBg(
                    if (isSelected) "#172d3a" else "#121a29",
                    if (isSelected) "#00f5d4" else "#263349",
                    10f
                )
                setPadding(dp(8), dp(6), dp(8), dp(6))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(5)
                }
                layoutParams = lp
            }

            val infoCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            val tvName = TextView(this).apply {
                text = "${cfg.name} • ${cfg.coinName} • Best: $lvBest"
                textSize = 11f
                setTextColor(if (isUnlocked) Color.WHITE else Color.parseColor("#94a3b8"))
                setTypeface(typeface, Typeface.BOLD)
            }
            val tvRule = TextView(this).apply {
                text = "${cfg.ruleNote} • Speed +${cfg.speedBonus}"
                textSize = 10f
                setTextColor(Color.parseColor("#94a3b8"))
            }
            infoCol.addView(tvName)
            infoCol.addView(tvRule)

            val actionBtn = Button(this).apply {
                textSize = 10f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(8), dp(2), dp(8), dp(2))
                if (isUnlocked) {
                    text = if (isSelected) "ACTIVE" else "SELECT"
                    setTextColor(if (isSelected) Color.parseColor("#071018") else Color.parseColor("#00f5d4"))
                    background = makeRoundedBg(
                        if (isSelected) "#00ff88" else "#1e293b",
                        "#00f5d4",
                        8f
                    )
                    setOnClickListener {
                        curLv = lvNum
                        saveState()
                        syncGameViewThemeAndSettings()
                        gameView.startNewMatch(autoBegin = false)
                        updateHud(0, gameView.getLevelBestScore(curLv), gameView.getSpeedMs(), curLv)
                        refreshMenuContent()
                        showToast("${cfg.name} selected (Best: ${gameView.getLevelBestScore(curLv)})")
                    }
                } else {
                    val canUnlock = lvNum == unlockedLevel + 1 && coins >= cost
                    text = "$cost PC"
                    setTextColor(if (canUnlock) Color.parseColor("#071018") else Color.parseColor("#ffbe0b"))
                    background = makeRoundedBg(
                        if (canUnlock) "#ffbe0b" else "#1e293b",
                        "#ffbe0b",
                        8f
                    )
                    setOnClickListener {
                        if (lvNum != unlockedLevel + 1) {
                            showToast("Unlock Lv${unlockedLevel + 1} first")
                        } else if (coins < cost) {
                            showToast("Need $cost Power Coins (Have $coins)")
                        } else {
                            coins -= cost
                            unlockedLevel = lvNum
                            curLv = lvNum
                            saveState()
                            syncGameViewThemeAndSettings()
                            gameView.startNewMatch(autoBegin = false)
                            refreshMenuContent()
                            showToast("${cfg.name} unlocked!")
                        }
                    }
                }
            }

            card.addView(infoCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(actionBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)))
            container.addView(card)
        }
    }

    private fun populateShopTab(container: LinearLayout) {
        val catRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
            layoutParams = lp
        }

        val cats = listOf("FOOD", "BODY", "FULL", "HEAD")
        for (cat in cats) {
            val selected = shopCategory == cat
            val btn = Button(this).apply {
                text = cat
                textSize = 10f
                setTextColor(if (selected) Color.parseColor("#071018") else Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                background = makeRoundedBg(
                    if (selected) "#ffbe0b" else "#182235",
                    if (selected) "#ffbe0b" else "#334155",
                    8f
                )
                setPadding(dp(4), dp(2), dp(4), dp(2))
                setOnClickListener {
                    shopCategory = cat
                    refreshMenuContent()
                }
            }
            val lp = LinearLayout.LayoutParams(0, dp(28), 1f).apply {
                if (cat != cats.last()) rightMargin = dp(4)
            }
            catRow.addView(btn, lp)
        }
        container.addView(catRow)

        when (shopCategory) {
            "FOOD" -> {
                for (i in GameData.foodItems.indices) {
                    val item = GameData.foodItems[i]
                    addShopRow(
                        container = container,
                        title = "${item.name} (${item.shape})",
                        price = item.price,
                        owned = unlockedFoods.contains(i),
                        equipped = foodIdx == i,
                        accentHex = item.colorHex,
                        onBuy = {
                            coins -= item.price
                            unlockedFoods.add(i)
                            foodIdx = i
                            saveState()
                            syncGameViewThemeAndSettings()
                            refreshMenuContent()
                            showToast("${item.name} unlocked!")
                        },
                        onEquip = {
                            foodIdx = i
                            saveState()
                            syncGameViewThemeAndSettings()
                            refreshMenuContent()
                            showToast("${item.name} equipped")
                        }
                    )
                }
            }
            "BODY" -> {
                for (i in GameData.bodyItems.indices) {
                    val item = GameData.bodyItems[i]
                    addShopRow(
                        container = container,
                        title = "${item.name} (${item.pattern})",
                        price = item.price,
                        owned = unlockedBodies.contains(i),
                        equipped = bodyIdx == i,
                        accentHex = item.colorHex,
                        onBuy = {
                            coins -= item.price
                            unlockedBodies.add(i)
                            bodyIdx = i
                            saveState()
                            syncGameViewThemeAndSettings()
                            refreshMenuContent()
                            showToast("${item.name} unlocked!")
                        },
                        onEquip = {
                            bodyIdx = i
                            saveState()
                            syncGameViewThemeAndSettings()
                            refreshMenuContent()
                            showToast("${item.name} equipped")
                        }
                    )
                }
            }
            "FULL" -> {
                for (i in GameData.fullBodies.indices) {
                    val item = GameData.fullBodies[i]
                    addShopRow(
                        container = container,
                        title = "${item.name} (${item.pattern})",
                        price = item.price,
                        owned = unlockedFulls.contains(i),
                        equipped = fullIdx == i,
                        accentHex = item.dotHex,
                        onBuy = {
                            coins -= item.price
                            unlockedFulls.add(i)
                            fullIdx = i
                            saveState()
                            syncGameViewThemeAndSettings()
                            refreshMenuContent()
                            showToast("${item.name} unlocked!")
                        },
                        onEquip = {
                            fullIdx = i
                            saveState()
                            syncGameViewThemeAndSettings()
                            refreshMenuContent()
                            showToast("${item.name} equipped")
                        }
                    )
                }
            }
            else -> {
                for (i in GameData.heads.indices) {
                    val item = GameData.heads[i]
                    addShopRow(
                        container = container,
                        title = "${item.emoji} ${item.name}",
                        price = item.price,
                        owned = unlockedHeads.contains(i),
                        equipped = headIdx == i,
                        accentHex = item.colorHex,
                        onBuy = {
                            coins -= item.price
                            unlockedHeads.add(i)
                            headIdx = i
                            saveState()
                            syncGameViewThemeAndSettings()
                            refreshMenuContent()
                            showToast("${item.name} unlocked!")
                        },
                        onEquip = {
                            headIdx = i
                            saveState()
                            syncGameViewThemeAndSettings()
                            refreshMenuContent()
                            showToast("${item.name} equipped")
                        }
                    )
                }
            }
        }
    }

    private fun addShopRow(
        container: LinearLayout,
        title: String,
        price: Int,
        owned: Boolean,
        equipped: Boolean,
        accentHex: String,
        onBuy: () -> Unit,
        onEquip: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = makeRoundedBg("#121a29", if (equipped) "#00ff88" else "#263349", 8f)
            setPadding(dp(8), dp(5), dp(8), dp(5))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(4)
            }
            layoutParams = lp
        }

        val dot = View(this).apply {
            background = makeRoundedBg(accentHex, "#ffffff", 99f)
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(8) }
        }

        val tv = TextView(this).apply {
            text = title
            textSize = 11f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }

        val btn = Button(this).apply {
            textSize = 10f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            if (owned) {
                text = if (equipped) "EQUIPPED" else "EQUIP"
                setTextColor(if (equipped) Color.parseColor("#071018") else Color.parseColor("#00f5d4"))
                background = makeRoundedBg(if (equipped) "#00ff88" else "#1e293b", "#00f5d4", 8f)
                setOnClickListener { onEquip() }
            } else {
                text = "$price PC"
                val affordable = coins >= price
                setTextColor(if (affordable) Color.parseColor("#071018") else Color.parseColor("#ffbe0b"))
                background = makeRoundedBg(if (affordable) "#ffbe0b" else "#1e293b", "#ffbe0b", 8f)
                setOnClickListener {
                    if (affordable) {
                        onBuy()
                    } else {
                        showToast("Need $price Power Coins (Have $coins)")
                    }
                }
            }
        }

        row.addView(dot)
        row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)))
        container.addView(row)
    }

    private fun populateSpinTab(container: LinearLayout) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = makeRoundedBg("#121a29", "#ffbe0b", 12f)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val tvDesc = TextView(this).apply {
            text = "Power Coin Spin • Rewards: ${GameData.spinCoinRewards.joinToString(", ")} Power Coins"
            textSize = 12f
            setTextColor(Color.parseColor("#ffbe0b"))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val tvSub = TextView(this).apply {
            text = "10 Foods = 1 Power Coin • Wallet: $coins PC"
            textSize = 10f
            setTextColor(Color.parseColor("#cbd5e1"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(10)
            }
            layoutParams = lp
        }

        val btnSpin = Button(this).apply {
            text = if (isSpinningAd) "SPINNING..." else "SPIN FOR POWER COIN"
            textSize = 11f
            setTextColor(Color.parseColor("#071018"))
            setTypeface(typeface, Typeface.BOLD)
            background = makeRoundedBg("#ffbe0b", "#fef08a", 10f)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            isEnabled = !isSpinningAd
            setOnClickListener {
                if (isSpinningAd) return@setOnClickListener
                isSpinningAd = true
                refreshMenuContent()

                val reward = GameData.spinCoinRewards.random()
                adManager?.showRewardedSpin(
                    onRewardEarned = {
                        runOnUiThread {
                            isSpinningAd = false
                            coins += reward
                            saveState()
                            updateHud(
                                score = gameView.getCurrentScore(),
                                bestScore = gameView.getLevelBestScore(curLv),
                                speedMs = gameView.getSpeedMs(),
                                level = curLv
                            )
                            refreshMenuContent()
                            showToast("Spin Won: +$reward Power Coins!")
                        }
                    },
                    onAdFailedOrClosed = {
                        runOnUiThread {
                            isSpinningAd = false
                            refreshMenuContent()
                            showToast("Spin cancelled or ad unavailable")
                        }
                    }
                ) ?: run {
                    isSpinningAd = false
                    coins += reward
                    saveState()
                    refreshMenuContent()
                    showToast("Spin Won: +$reward Power Coins!")
                }
            }
        }

        box.addView(tvDesc)
        box.addView(tvSub)
        box.addView(btnSpin)
        container.addView(box)
    }

    private fun populateMoreTab(container: LinearLayout) {
        val bonusBtn = Button(this).apply {
            text = if (ratedBonusClaimed) {
                "RATE BONUS CLAIMED (+${GameData.RATE_BONUS_COINS} PC)"
            } else {
                "RATE ON UPTODOWN (+${GameData.RATE_BONUS_COINS} PC)"
            }
            textSize = 11f
            setTextColor(Color.parseColor("#071018"))
            setTypeface(typeface, Typeface.BOLD)
            background = makeRoundedBg(
                if (ratedBonusClaimed) "#64748b" else "#00ff88",
                "#00ff88",
                10f
            )
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(36)
            ).apply {
                bottomMargin = dp(8)
            }
            layoutParams = lp
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GameData.UPTODOWN_URL)))
                } catch (_: Exception) {
                }
                if (!ratedBonusClaimed) {
                    ratedBonusClaimed = true
                    coins += GameData.RATE_BONUS_COINS
                    saveState()
                    updateHud(
                        score = gameView.getCurrentScore(),
                        bestScore = gameView.getLevelBestScore(curLv),
                        speedMs = gameView.getSpeedMs(),
                        level = curLv
                    )
                    refreshMenuContent()
                    showToast("+${GameData.RATE_BONUS_COINS} Power Coins bonus added!")
                }
            }
        }

        val infoText = TextView(this).apply {
            text = "Developer: ${GameData.DEVELOPER} • Studio: ${GameData.STUDIO}\n" +
                "Each level tracks its own Best Score independently.\n" +
                "Use the ⚙ gear button at top-right for 24h Reminder & Model options."
            textSize = 11f
            setTextColor(Color.parseColor("#cbd5e1"))
            background = makeRoundedBg("#121a29", "#263349", 10f)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        container.addView(bonusBtn)
        container.addView(infoText)
    }

    private fun refreshSettingsContent() {
        settingsPanel.removeAllViews()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
            layoutParams = lp
        }

        val title = TextView(this).apply {
            text = "⚙ SETTINGS & MODELS"
            textSize = 13f
            setTextColor(Color.parseColor("#ffbe0b"))
            setTypeface(typeface, Typeface.BOLD)
        }

        val btnDone = Button(this).apply {
            text = "DONE"
            textSize = 10f
            setTextColor(Color.parseColor("#071018"))
            setTypeface(typeface, Typeface.BOLD)
            background = makeRoundedBg("#00f5d4", "#00f5d4", 8f)
            setPadding(dp(10), dp(2), dp(10), dp(2))
            setOnClickListener { closeOverlays(resumeIfPaused = true) }
        }

        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(btnDone, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)))

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 24h Reminder ON / OFF toggle
        body.addView(
            makeToggleRow(
                label = "24h Reminder Notification",
                stateText = if (reminderOn) "ON" else "OFF",
                enabled = reminderOn,
                onClick = {
                    val nextState = !reminderOn
                    if (nextState && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (!hasPerm) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@makeToggleRow
                        }
                    }
                    reminderOn = nextState
                    NotificationHelper.setReminderEnabled(this, reminderOn)
                    refreshSettingsContent()
                    showToast("24h Reminder ${if (reminderOn) "ON" else "OFF"}")
                }
            )
        )

        body.addView(
            makeToggleRow(
                label = "Soft Game Sound",
                stateText = if (soundOn) "ON" else "OFF",
                enabled = soundOn,
                onClick = {
                    soundOn = !soundOn
                    saveState()
                    syncGameViewThemeAndSettings()
                    refreshSettingsContent()
                }
            )
        )

        body.addView(
            makeToggleRow(
                label = "Haptic Vibration",
                stateText = if (vibOn) "ON" else "OFF",
                enabled = vibOn,
                onClick = {
                    vibOn = !vibOn
                    saveState()
                    syncGameViewThemeAndSettings()
                    refreshSettingsContent()
                }
            )
        )

        body.addView(
            makeToggleRow(
                label = "Snake Trail Effect",
                stateText = if (trailOn) "ON" else "OFF",
                enabled = trailOn,
                onClick = {
                    trailOn = !trailOn
                    saveState()
                    syncGameViewThemeAndSettings()
                    refreshSettingsContent()
                }
            )
        )

        // Separate Model / Preset Options section inside Settings
        val modelSectionLabel = TextView(this).apply {
            text = "MODEL & PRESET OPTIONS"
            textSize = 11f
            setTextColor(Color.parseColor("#00f5d4"))
            setTypeface(typeface, Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(4)
            }
            layoutParams = lp
        }
        body.addView(modelSectionLabel)

        val presetScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
        }
        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val presets = listOf("AUTO", "CLASSIC", "COMPACT", "TURBO")
        for (mode in presets) {
            val active = modelPresetMode == mode
            val pBtn = Button(this).apply {
                text = mode
                textSize = 10f
                setTextColor(if (active) Color.parseColor("#071018") else Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                background = makeRoundedBg(
                    if (active) "#00ff88" else "#182235",
                    if (active) "#00ff88" else "#334155",
                    8f
                )
                setPadding(dp(10), dp(2), dp(10), dp(2))
                setOnClickListener {
                    applyModelPreset(mode)
                    refreshSettingsContent()
                }
            }
            presetRow.addView(pBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)).apply {
                rightMargin = dp(6)
            })
        }
        presetScroll.addView(presetRow)
        body.addView(presetScroll)

        body.addView(
            makeToggleRow(
                label = "Show Level & Coin Model Info",
                stateText = if (showModelBadge) "SHOWN" else "HIDDEN",
                enabled = showModelBadge,
                onClick = {
                    showModelBadge = !showModelBadge
                    saveState()
                    updateHud(
                        score = gameView.getCurrentScore(),
                        bestScore = gameView.getLevelBestScore(curLv),
                        speedMs = gameView.getSpeedMs(),
                        level = curLv
                    )
                    refreshSettingsContent()
                }
            )
        )

        scroll.addView(body)
        settingsPanel.addView(header)
        settingsPanel.addView(scroll)
    }

    private fun applyModelPreset(mode: String) {
        modelPresetMode = mode
        when (mode) {
            "CLASSIC" -> {
                foodIdx = 0
                bodyIdx = 0
                fullIdx = 0
                headIdx = 0
                trailOn = false
            }
            "COMPACT" -> {
                bodyIdx = 2
                fullIdx = 1
                trailOn = true
            }
            "TURBO" -> {
                bodyIdx = 4
                fullIdx = 4
                headIdx = 6
                trailOn = true
            }
            else -> {
                trailOn = true
            }
        }
        unlockedFoods.add(foodIdx)
        unlockedBodies.add(bodyIdx)
        unlockedFulls.add(fullIdx)
        unlockedHeads.add(headIdx)
        saveState()
        syncGameViewThemeAndSettings()
        showToast("Model preset: $mode")
    }

    private fun makeToggleRow(
        label: String,
        stateText: String,
        enabled: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = makeRoundedBg("#121a29", "#263349", 8f)
            setPadding(dp(8), dp(5), dp(8), dp(5))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(5)
            }
            layoutParams = lp

            val tv = TextView(this@MainActivity).apply {
                text = label
                textSize = 11f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            }

            val btn = Button(this@MainActivity).apply {
                text = stateText
                textSize = 10f
                setTextColor(if (enabled) Color.parseColor("#071018") else Color.parseColor("#cbd5e1"))
                setTypeface(typeface, Typeface.BOLD)
                background = makeRoundedBg(
                    if (enabled) "#00ff88" else "#334155",
                    if (enabled) "#00ff88" else "#64748b",
                    8f
                )
                setPadding(dp(10), dp(2), dp(10), dp(2))
                setOnClickListener { onClick() }
            }

            addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)))
        }
    }

    private fun syncGameViewThemeAndSettings() {
        gameView.configureSettings(sound = soundOn, vib = vibOn, trailEnabled = trailOn)
        gameView.applyTheme(
            level = curLv,
            fIdx = foodIdx,
            bIdx = bodyIdx,
            flIdx = fullIdx,
            hIdx = headIdx
        )
    }

    private fun updateHud(score: Int, bestScore: Int, speedMs: Int, level: Int) {
        val cfg = GameData.safeLevel(level)
        tvCoin.text = "PC $coins"
        tvBest.text = "BEST $bestScore"
        tvScore.text = "SCR $score"
        tvSpeed.text = "${speedMs}ms"
        tvLevelInfo.text = if (showModelBadge) {
            "${cfg.name} • ${cfg.coinName} • Model: $modelPresetMode"
        } else {
            "${cfg.name} • Lv$level/15"
        }
    }

    private fun setupBannerAdSafe() {
        bannerHost.removeAllViews()
        try {
            val wv = WebView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            bannerWebView = wv
            bannerHost.addView(wv)
            adManager?.attachBanner(wv)
        } catch (_: Exception) {
            bannerWebView = null
        }
    }

    private fun showToast(message: String) {
        val banner = toastBanner ?: return
        hideToastRunnable?.let { uiHandler.removeCallbacks(it) }
        banner.text = message
        banner.visibility = View.VISIBLE
        val r = Runnable {
            banner.visibility = View.GONE
        }
        hideToastRunnable = r
        uiHandler.postDelayed(r, 2200L)
    }

    private fun makeRoundedBg(fillHex: String, strokeHex: String, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            setColor(parseColorSafe(fillHex, Color.DKGRAY))
            setStroke(dp(1), parseColorSafe(strokeHex, Color.GRAY))
        }
    }

    private fun parseColorSafe(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
        reminderOn = NotificationHelper.isReminderEnabled(this)
        if (reminderOn) {
            NotificationHelper.scheduleDailyReminder(this)
        }
        try {
            bannerWebView?.onResume()
        } catch (_: Exception) {
        }
    }

    override fun onPause() {
        super.onPause()
        gameView.stopLoop()
        try {
            bannerWebView?.onPause()
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gameView.stopLoop()
        hideToastRunnable?.let { uiHandler.removeCallbacks(it) }
        uiHandler.removeCallbacksAndMessages(null)
        try {
            bannerWebView?.apply {
                stopLoading()
                removeAllViews()
                destroy()
            }
        } catch (_: Exception) {
        }
        bannerWebView = null
        adManager = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (settingsVisible || menuVisible) {
                closeOverlays(resumeIfPaused = true)
                return true
            }
            if (!isFinishingExit) {
                isFinishingExit = true
                gameView.stopLoop()
                finish()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
