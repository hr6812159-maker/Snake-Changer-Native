package com.sadiastudio.snakechanger

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var adManager: AdManager

    private lateinit var rootContainer: FrameLayout
    private lateinit var bannerContainer: FrameLayout
    private lateinit var tvPlayerId: TextView
    private lateinit var tvUnlockInfo: TextView
    private lateinit var tvLvBadge: TextView
    private lateinit var tvCoinTop: TextView
    private lateinit var tvScoreLvTop: TextView
    private lateinit var tvSpeedTop: TextView
    private lateinit var tvSoundTop: TextView
    private lateinit var gameView: GameView
    private lateinit var btnOkControl: Button

    private lateinit var splashOverlay: LinearLayout
    private lateinit var leftDrawer: LinearLayout
    private lateinit var rightDrawer: LinearLayout

    private var playerId = ""
    private var coins = GameData.START_COINS
    private var highScore = 0
    private var curLv = 1
    private var soundOn = true
    private var vibOn = true
    private var trailOn = true
    private var selFood = 0
    private var selBody = 0
    private var selFull = 0
    private var selHead = 0

    private var backPressCount = 0
    private val uiHandler = Handler(Looper.getMainLooper())
    private var activeModalDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("sgs_snake_prefs", MODE_PRIVATE)
        adManager = AdManager(this)

        initPrefsState()
        buildUi()
        adManager.attachBanner(bannerContainer)
        applyLevelTheme(syncFromLevel = true)
        syncAllBars()

        val uid = prefs.getString("uid", null) ?: "guest_$playerId".also {
            prefs.edit().putString("uid", it).apply()
        }
        adManager.syncFirebaseUser(uid, coins, curLv, highScore) { rCoins, rLv, rHigh ->
            var changed = false
            if (rCoins != null && rCoins > coins) {
                coins = rCoins
                saveCoins(coins)
                changed = true
            }
            if (rLv != null && rLv in 1..15 && isLevelUnlocked(rLv) && rLv > curLv) {
                curLv = rLv
                prefs.edit().putInt("curLv", curLv).apply()
                changed = true
            }
            if (rHigh != null && rHigh > highScore) {
                highScore = rHigh
                prefs.edit().putInt("h", highScore).apply()
                changed = true
            }
            if (changed) {
                applyLevelTheme(syncFromLevel = true)
                syncAllBars()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleSystemBack()
            }
        })
    }

    private fun initPrefsState() {
        if (!prefs.contains("sgs_secure_c")) {
            val oldC = prefs.getInt("c", GameData.START_COINS)
            prefs.edit().putString("sgs_secure_c", GameData.encodeCoins(oldC)).apply()
        }
        coins = GameData.decodeCoins(prefs.getString("sgs_secure_c", null))

        curLv = prefs.getInt("curLv", 1).coerceIn(1, 15)
        prefs.edit().putInt("curLv", curLv).apply()

        highScore = prefs.getInt("h", 0).coerceAtLeast(0)
        prefs.edit().putInt("h", highScore).apply()

        soundOn = prefs.getString("soundOn", "1") != "0"
        vibOn = prefs.getString("vibOn", "1") != "0"
        trailOn = prefs.getString("trailOn", "1") != "0"

        var pid = prefs.getString("sgs_player_id", null)
        if (pid.isNullOrBlank() || !pid.startsWith("SGS-")) {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            val randPart = buildString {
                repeat(6) { append(chars[Random.nextInt(chars.length)]) }
            }
            pid = "SGS-$randPart"
            prefs.edit().putString("sgs_player_id", pid).apply()
        }
        playerId = pid
    }

    private fun saveCoins(value: Int) {
        coins = value.coerceAtLeast(0)
        prefs.edit().putString("sgs_secure_c", GameData.encodeCoins(coins)).apply()
        gameView.setCoinsValue(coins)
        syncAllBars()
    }

    private fun isLevelUnlocked(lv: Int): Boolean {
        if (lv <= 1) return true
        return prefs.getString("lvUnlock_$lv", "0") == "1"
    }

    private fun maxUnlockedLevel(): Int {
        var maxLv = 1
        for (i in 1..15) {
            if (isLevelUnlocked(i)) maxLv = i
        }
        return maxLv
    }

    private fun buildUi() {
        rootContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#080810"))
        }

        val mainColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        bannerContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#111111"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111111"))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#3300FFFF"))
            }
        }
        mainColumn.addView(
            bannerContainer,
            LinearLayout.LayoutParams(dp(320), dp(50)).apply {
                topMargin = dp(2)
                bottomMargin = dp(8)
            }
        )

        val playerCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111111"))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(2), Color.parseColor("#00FFFF"))
            }
            setOnClickListener { openProfileDialog() }
        }

        val avatarView = ImageView(this).apply {
            setImageBitmap(createAvatarBitmap(dp(36)))
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dp(6).toFloat()
                setStroke(dp(2), Color.WHITE)
            }
        }
        playerCard.addView(avatarView, LinearLayout.LayoutParams(dp(36), dp(36)))

        val idCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(6), 0)
        }
        tvPlayerId = TextView(this).apply {
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 10f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        tvUnlockInfo = TextView(this).apply {
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 8f
        }
        idCol.addView(tvPlayerId)
        idCol.addView(tvUnlockInfo)
        playerCard.addView(idCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        tvLvBadge = TextView(this).apply {
            setTextColor(Color.BLACK)
            textSize = 10f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00FFFF"))
                cornerRadius = dp(12).toFloat()
            }
        }
        playerCard.addView(tvLvBadge)

        mainColumn.addView(
            playerCard,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        )

        val gameCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#11111EE8"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(2), Color.parseColor("#00FFFF"))
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }

        val canvasTopBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        tvCoinTop = TextView(this).apply {
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 9f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        tvScoreLvTop = TextView(this).apply {
            setTextColor(Color.parseColor("#FFEB3B"))
            textSize = 9f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        tvSpeedTop = TextView(this).apply {
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 9f
            gravity = Gravity.END
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        tvSoundTop = TextView(this).apply {
            setTextColor(Color.parseColor("#00FF88"))
            textSize = 9f
            gravity = Gravity.END
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener { toggleSound() }
        }

        canvasTopBar.addView(tvCoinTop, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        canvasTopBar.addView(tvScoreLvTop, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f))
        canvasTopBar.addView(tvSpeedTop, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        canvasTopBar.addView(tvSoundTop, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        gameCard.addView(canvasTopBar)

        gameView = GameView(this).apply {
            listener = object : GameView.Listener {
                override fun onCoinsEarned(newCoins: Int, isSpecial: Boolean) {
                    saveCoins(newCoins)
                    adManager.syncFirebaseUser(
                        prefs.getString("uid", "guest_$playerId") ?: "guest_$playerId",
                        coins,
                        curLv,
                        highScore
                    )
                }

                override fun onScoreUpdated(score: Int, highScore: Int, speedMs: Long) {
                    if (highScore > this@MainActivity.highScore) {
                        this@MainActivity.highScore = highScore
                        prefs.edit().putInt("h", highScore).apply()
                    }
                    tvScoreLvTop.text = "SC $score | Lv $curLv"
                    tvSpeedTop.text = "SPD ${speedMs}ms"
                }

                override fun onGameOver(finalScore: Int, highScore: Int) {
                    this@MainActivity.highScore = maxOf(this@MainActivity.highScore, highScore)
                    prefs.edit().putInt("h", this@MainActivity.highScore).apply()
                    btnOkControl.text = "OK"
                    adManager.syncFirebaseUser(
                        prefs.getString("uid", "guest_$playerId") ?: "guest_$playerId",
                        coins,
                        curLv,
                        this@MainActivity.highScore
                    )
                }

                override fun onPauseChanged(paused: Boolean, waiting: Boolean) {
                    btnOkControl.text = if (!paused && !waiting) "PAUSE" else "OK"
                }

                override fun onWinLevel() {
                    Toast.makeText(this@MainActivity, "YOU WIN!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        gameCard.addView(
            gameView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        mainColumn.addView(
            gameCard,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(4)
                bottomMargin = dp(6)
            }
        )

        val hintTv = TextView(this).apply {
            text = "← Tap MENU / SET Handles for Drawers →"
            setTextColor(Color.parseColor("#777777"))
            textSize = 8f
            gravity = Gravity.CENTER
        }
        mainColumn.addView(hintTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6)
        })

        val dpad = buildDpad()
        mainColumn.addView(dpad, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6)
        })

        rootContainer.addView(
            mainColumn,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        val leftHandle = TextView(this).apply {
            text = "MENU"
            setTextColor(Color.BLACK)
            textSize = 8f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DD00FFFF"))
                cornerRadii = floatArrayOf(0f, 0f, dp(6).toFloat(), dp(6).toFloat(), dp(6).toFloat(), dp(6).toFloat(), 0f, 0f)
            }
            setOnClickListener { toggleLeftDrawer() }
        }
        rootContainer.addView(
            leftHandle,
            FrameLayout.LayoutParams(dp(22), dp(46)).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
        )

        val rightHandle = TextView(this).apply {
            text = "SET"
            setTextColor(Color.BLACK)
            textSize = 8f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DD00FF88"))
                cornerRadii = floatArrayOf(dp(6).toFloat(), dp(6).toFloat(), 0f, 0f, 0f, 0f, dp(6).toFloat(), dp(6).toFloat())
            }
            setOnClickListener { toggleRightDrawer() }
        }
        rootContainer.addView(
            rightHandle,
            FrameLayout.LayoutParams(dp(22), dp(46)).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
        )

        leftDrawer = buildLeftDrawer()
        rootContainer.addView(
            leftDrawer,
            FrameLayout.LayoutParams(dp(170), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                leftMargin = dp(24)
            }
        )

        rightDrawer = buildRightDrawer()
        rootContainer.addView(
            rightDrawer,
            FrameLayout.LayoutParams(dp(170), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                rightMargin = dp(24)
            }
        )

        splashOverlay = buildSplashOverlay()
        rootContainer.addView(
            splashOverlay,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        setContentView(rootContainer)
    }

    private fun buildDpad(): LinearLayout {
        val boxSize = dp(64)
        val btnH = dp(48)
        val gap = dp(6)

        fun makePadBtn(label: String, bgHex: String, fgHex: String, onClick: () -> Unit): Button {
            return Button(this).apply {
                text = label
                setTextColor(Color.parseColor(fgHex))
                textSize = if (label == "OK" || label == "PAUSE") 11f else 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                isAllCaps = false
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(bgHex))
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(2), Color.parseColor("#8800FFFF"))
                }
                setOnClickListener {
                    closeDrawers()
                    onClick()
                }
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            val r1 = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            r1.addView(makePadBtn("↑", "#15151F", "#00FFFF") { gameView.setDirection(GameView.Direction.UP) }, LinearLayout.LayoutParams(boxSize, btnH))
            addView(r1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = gap })

            val r2 = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            r2.addView(makePadBtn("←", "#15151F", "#00FFFF") { gameView.setDirection(GameView.Direction.LEFT) }, LinearLayout.LayoutParams(boxSize, btnH).apply { marginEnd = gap })
            btnOkControl = makePadBtn("OK", "#00FFFF", "#000000") { gameView.togglePauseOrStart() }
            r2.addView(btnOkControl, LinearLayout.LayoutParams(boxSize, btnH).apply { marginEnd = gap })
            r2.addView(makePadBtn("→", "#15151F", "#00FFFF") { gameView.setDirection(GameView.Direction.RIGHT) }, LinearLayout.LayoutParams(boxSize, btnH))
            addView(r2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = gap })

            val r3 = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            r3.addView(makePadBtn("↓", "#15151F", "#00FFFF") { gameView.setDirection(GameView.Direction.DOWN) }, LinearLayout.LayoutParams(boxSize, btnH))
            addView(r3)
        }
    }

    private fun buildLeftDrawer(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5000000"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(2), Color.parseColor("#00FFFF"))
            }

            addView(TextView(this@MainActivity).apply {
                text = "LEFT MENU"
                setTextColor(Color.parseColor("#00FFFF"))
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(drawerBtn("SHOP UNIQUE", "#15151F", "#00FFFF") { openShopDialog() })
            addView(drawerBtn("LEVELS LOCK", "#15151F", "#00FFFF") { openLevelsDialog() })
            addView(drawerBtn("SPIN WHEEL", "#15151F", "#00FFFF") { openSpinDialog() })
            addView(drawerBtn("EXIT APP", "#FF4444", "#FFFFFF") { exitGame() })
            addView(drawerBtn("CLOSE", "#15151F", "#00FFFF") { closeDrawers() })
        }
    }

    private fun buildRightDrawer(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5000000"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(2), Color.parseColor("#00FF88"))
            }

            addView(TextView(this@MainActivity).apply {
                text = "RIGHT MENU"
                setTextColor(Color.parseColor("#00FF88"))
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(drawerBtn("SETTINGS", "#00FF88", "#000000") { openSettingsDialog() })
            addView(drawerBtn("SOUND ON/OFF", "#15151F", "#00FFFF") { toggleSound() })
            addView(drawerBtn("OK PAUSE", "#00FFFF", "#000000") { gameView.togglePauseOrStart() })
        }
    }

    private fun drawerBtn(label: String, bgHex: String, fgHex: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.parseColor(fgHex))
            textSize = 9f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#5500FFFF"))
            }
            setOnClickListener {
                closeDrawers()
                onClick()
            }
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)).apply {
                topMargin = dp(6)
            }
        }
    }

    private fun buildSplashOverlay(): LinearLayout {
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val logo = TextView(this).apply {
            text = GameData.BRAND
            setTextColor(Color.BLACK)
            textSize = 20f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#00FF88"), Color.parseColor("#00FFFF"))
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(2), Color.WHITE)
            }
        }
        overlay.addView(logo, LinearLayout.LayoutParams(dp(84), dp(84)).apply { bottomMargin = dp(16) })

        val stepLabels = listOf(
            "☐ Engine Starting...",
            "☐ Levels Checking...",
            "☐ Skins Checking...",
            "☐ Coins & Server Sync...",
            "☐ Player Ready..."
        )
        val doneLabels = listOf(
            "☑ Engine Hissing Active - SGS Core",
            "☑ 15 Levels OK - 15 Snakes Found",
            "☑ Skins OK - Food Body Head Ready",
            "☑ Coins Encrypted & Server Secure",
            "☑ Player Ready - Snake Changer SGS"
        )
        val stepViews = stepLabels.map { raw ->
            TextView(this).apply {
                text = raw
                setTextColor(Color.parseColor("#777777"))
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(3), 0, dp(3))
                overlay.addView(this)
            }
        }

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressDrawable.setColorFilter(Color.parseColor("#00FF88"), android.graphics.PorterDuff.Mode.SRC_IN)
        }
        overlay.addView(progressBar, LinearLayout.LayoutParams(dp(260), dp(10)).apply { topMargin = dp(14) })

        val pctTv = TextView(this).apply {
            text = "0%"
            setTextColor(Color.parseColor("#00FF88"))
            textSize = 10f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        overlay.addView(pctTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })

        val tapBtn = Button(this).apply {
            text = "TAP TO ENTER - SGS"
            visibility = View.GONE
            setTextColor(Color.BLACK)
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00FF88"))
                cornerRadius = dp(20).toFloat()
            }
            setOnClickListener {
                overlay.visibility = View.GONE
                gameView.resetForLevel(keepWaiting = true)
            }
        }
        overlay.addView(tapBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(42)).apply { topMargin = dp(14) })

        val thresholds = intArrayOf(5, 28, 48, 70, 88)
        var pct = 0
        val loaderRunnable = object : Runnable {
            override fun run() {
                pct += 2
                if (pct > 100) pct = 100
                progressBar.progress = pct
                pctTv.text = "$pct%"
                thresholds.forEachIndexed { idx, th ->
                    if (pct >= th) {
                        stepViews[idx].text = doneLabels[idx]
                        stepViews[idx].setTextColor(Color.parseColor("#00FF88"))
                    }
                }
                if (pct < 100) {
                    uiHandler.postDelayed(this, 60L)
                } else {
                    tapBtn.visibility = View.VISIBLE
                }
            }
        }
        uiHandler.postDelayed(loaderRunnable, 80L)
        uiHandler.postDelayed({
            if (overlay.visibility == View.VISIBLE) {
                overlay.visibility = View.GONE
                gameView.resetForLevel(keepWaiting = true)
            }
        }, 5500L)

        return overlay
    }

    private fun applyLevelTheme(syncFromLevel: Boolean) {
        curLv = curLv.coerceIn(1, 15)
        prefs.edit().putInt("curLv", curLv).apply()
        val cfg = GameData.safeLevel(curLv)
        if (syncFromLevel) {
            selFood = cfg.food
            selBody = cfg.body
            selFull = cfg.full
            selHead = cfg.head
        }
        gameView.configure(curLv, coins, highScore, trailOn, selFood, selBody, selFull, selHead)
    }

    private fun syncAllBars() {
        val maxLv = maxUnlockedLevel()
        tvPlayerId.text = "ID: $playerId"
        tvUnlockInfo.text = "Unlock: Lv $maxLv/15 | Left: ${15 - maxLv}"
        tvLvBadge.text = "Lv $curLv"
        tvCoinTop.text = "COIN $coins"
        tvScoreLvTop.text = "SC 0 | Lv $curLv"
        tvSpeedTop.text = "SPD ${gameView.getSpeedMs()}ms"
        tvSoundTop.text = if (soundOn) "SOUND" else "MUTE"
    }

    private fun toggleLeftDrawer() {
        val nextVis = if (leftDrawer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        leftDrawer.visibility = nextVis
        rightDrawer.visibility = nextVis
    }

    private fun toggleRightDrawer() {
        toggleLeftDrawer()
    }

    private fun closeDrawers() {
        leftDrawer.visibility = View.GONE
        rightDrawer.visibility = View.GONE
    }

    private fun toggleSound() {
        soundOn = !soundOn
        prefs.edit().putString("soundOn", if (soundOn) "1" else "0").apply()
        syncAllBars()
    }

    private fun toggleVib() {
        vibOn = !vibOn
        prefs.edit().putString("vibOn", if (vibOn) "1" else "0").apply()
    }

    private fun toggleTrail() {
        trailOn = !trailOn
        prefs.edit().putString("trailOn", if (trailOn) "1" else "0").apply()
        gameView.applyTheme(curLv, trailOn, selFood, selBody, selFull, selHead)
    }

    private fun openProfileDialog() {
        gameView.pauseForMenu()
        dismissActiveDialog()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(dp(18), dp(18), dp(18), dp(18))
            addView(ImageView(this@MainActivity).apply {
                setImageBitmap(createAvatarBitmap(dp(70)))
            })
            addView(TextView(this@MainActivity).apply {
                text = "SGS PLAYER ID: $playerId"
                setTextColor(Color.parseColor("#00FFFF"))
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(10), 0, dp(4))
            })
            addView(TextView(this@MainActivity).apply {
                text = "Secure ID - Locked & Protected"
                setTextColor(Color.GRAY)
                textSize = 9f
                gravity = Gravity.CENTER
            })
        }
        activeModalDialog = AlertDialog.Builder(this)
            .setTitle("PLAYER PROFILE & SGS ID")
            .setView(box)
            .setPositiveButton("CLOSE") { d, _ -> d.dismiss() }
            .show()
    }

    private fun openShopDialog() {
        gameView.pauseForMenu()
        dismissActiveDialog()

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        fun rebuildShopItems(tabIdx: Int) {
            listContainer.removeAllViews()
            when (tabIdx) {
                0 -> GameData.foodItems.forEachIndexed { idx, it ->
                    val unlocked = prefs.getString("fP_$idx", "0") == "1"
                    val isSelected = selFood == idx
                    listContainer.addView(shopRow(it.nWithDesc(), it.colorHex, it.price, unlocked, isSelected) {
                        buyShopItem("fP_$idx", it.price) {
                            selFood = idx
                            gameView.applyTheme(curLv, trailOn, selFood, selBody, selFull, selHead)
                            rebuildShopItems(0)
                        }
                    })
                }
                1 -> GameData.bodyItems.forEachIndexed { idx, it ->
                    val unlocked = prefs.getString("bP_$idx", "0") == "1"
                    val isSelected = selBody == idx
                    listContainer.addView(shopRow(it.nWithDesc(), it.colorHex, it.price, unlocked, isSelected) {
                        buyShopItem("bP_$idx", it.price) {
                            selBody = idx
                            gameView.applyTheme(curLv, trailOn, selFood, selBody, selFull, selHead)
                            rebuildShopItems(1)
                        }
                    })
                }
                2 -> GameData.fullBodies.forEachIndexed { idx, it ->
                    val unlocked = prefs.getString("fbP_$idx", "0") == "1"
                    val isSelected = selFull == idx
                    listContainer.addView(shopRow(it.nWithDesc(), it.dotHex, it.price, unlocked, isSelected) {
                        buyShopItem("fbP_$idx", it.price) {
                            selFull = idx
                            gameView.applyTheme(curLv, trailOn, selFood, selBody, selFull, selHead)
                            rebuildShopItems(2)
                        }
                    })
                }
                else -> GameData.heads.forEachIndexed { idx, it ->
                    val unlocked = prefs.getString("hP_$idx", "0") == "1"
                    val isSelected = selHead == idx
                    listContainer.addView(shopRow(it.nWithDesc(), it.colorHex, it.price, unlocked, isSelected) {
                        buyShopItem("hP_$idx", it.price) {
                            selHead = idx
                            gameView.applyTheme(curLv, trailOn, selFood, selBody, selFull, selHead)
                            rebuildShopItems(3)
                        }
                    })
                }
            }
        }

        val rootShop = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A12"))
        }
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(4))
        }
        val tabNames = listOf("Food", "Body", "Full", "Head")
        tabNames.forEachIndexed { i, title ->
            tabBar.addView(Button(this).apply {
                text = title
                textSize = 9f
                isAllCaps = false
                setOnClickListener { rebuildShopItems(i) }
            }, LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) })
        }
        rootShop.addView(tabBar)
        rootShop.addView(ScrollView(this).apply { addView(listContainer) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360)))

        rebuildShopItems(0)
        activeModalDialog = AlertDialog.Builder(this)
            .setTitle("SHOP UNIQUE - ${GameData.GAME_NAME}")
            .setView(rootShop)
            .setPositiveButton("CLOSE") { d, _ -> d.dismiss() }
            .show()
    }

    private fun shopRow(
        title: String,
        dotHex: String,
        price: Int,
        unlocked: Boolean,
        selected: Boolean,
        onAction: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#15151F"))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.parseColor("#333344"))
            }
            addView(View(this@MainActivity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(dotHex))
                    setStroke(dp(2), Color.WHITE)
                }
            }, LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(8) })

            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 10f
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(Button(this@MainActivity).apply {
                text = when {
                    selected -> "USING"
                    unlocked -> "USE"
                    else -> "$price COIN"
                }
                textSize = 9f
                isAllCaps = false
                setOnClickListener { onAction() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)))
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(6)
            }
        }
    }

    private fun buyShopItem(prefKey: String, price: Int, onSuccess: () -> Unit) {
        if (prefs.getString(prefKey, "0") == "1") {
            onSuccess()
            return
        }
        if (coins >= price) {
            saveCoins(coins - price)
            prefs.edit().putString(prefKey, "1").apply()
            adManager.syncFirebaseUser(
                prefs.getString("uid", "guest_$playerId") ?: "guest_$playerId",
                coins,
                curLv,
                highScore
            )
            onSuccess()
        } else {
            Toast.makeText(this, "Need $price Coin - Have $coins - Spin for Coins", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openLevelsDialog() {
        gameView.pauseForMenu()
        dismissActiveDialog()
        val labels = (1..15).map { lv ->
            val cfg = GameData.safeLevel(lv)
            val cost = GameData.levelCosts[lv - 1]
            val unlocked = isLevelUnlocked(lv)
            val status = if (unlocked) "UNLOCKED" else "LOCK ${cost} Coin"
            "Lv$lv ${GameData.fullBodies[cfg.full].name} [$status]"
        }.toTypedArray()

        activeModalDialog = AlertDialog.Builder(this)
            .setTitle("LEVELS - 15 Levels 15 Snakes")
            .setItems(labels) { _, which ->
                val targetLv = which + 1
                if (isLevelUnlocked(targetLv)) {
                    curLv = targetLv
                    applyLevelTheme(syncFromLevel = true)
                    syncAllBars()
                } else {
                    val cost = GameData.levelCosts[which]
                    if (coins >= cost) {
                        saveCoins(coins - cost)
                        prefs.edit().putString("lvUnlock_$targetLv", "1").putInt("curLv", targetLv).apply()
                        curLv = targetLv
                        applyLevelTheme(syncFromLevel = true)
                        syncAllBars()
                        adManager.syncFirebaseUser(
                            prefs.getString("uid", "guest_$playerId") ?: "guest_$playerId",
                            coins,
                            curLv,
                            highScore
                        )
                        Toast.makeText(this, "Level $targetLv Unlocked!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Need $cost Coins - You have $coins", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun openSpinDialog() {
        gameView.pauseForMenu()
        dismissActiveDialog()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val adHost = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#161622"))
        }
        box.addView(adHost, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(10) })
        adManager.loadRewardedSpinAd(adHost)

        val statusTv = TextView(this).apply {
            text = "Spin rewards: +1500 / +2000 / +3000 Coins"
            setTextColor(Color.parseColor("#00FF88"))
            textSize = 11f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        box.addView(statusTv)

        activeModalDialog = AlertDialog.Builder(this)
            .setTitle("SPIN WHEEL - ${GameData.GAME_NAME}")
            .setView(box)
            .setPositiveButton("SPIN (+1500/3000)") { _, _ ->
                val reward = GameData.spinRewards[Random.nextInt(GameData.spinRewards.size)]
                saveCoins(coins + reward)
                adManager.syncFirebaseUser(
                    prefs.getString("uid", "guest_$playerId") ?: "guest_$playerId",
                    coins,
                    curLv,
                    highScore
                )
                Toast.makeText(this, "Won $reward Coins! - ${GameData.GAME_NAME}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun openSettingsDialog() {
        gameView.pauseForMenu()
        dismissActiveDialog()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0E0E16"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        container.addView(TextView(this).apply {
            text = "ID: $playerId | Lv: $curLv | Coin: $coins\nStudio: ${GameData.STUDIO} | Dev: ${GameData.DEVELOPER}\nPrivacy: Coins & scores stored via local & Firebase Cloud."
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 10f
            setPadding(0, 0, 0, dp(10))
        })

        container.addView(Button(this).apply {
            text = "RATE US (+${GameData.RATE_BONUS_COINS} Coins)"
            isAllCaps = false
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.uptodown.com/")))
                } catch (_: Exception) {
                }
                if (prefs.getString("rated", "0") != "1") {
                    prefs.edit().putString("rated", "1").apply()
                    saveCoins(coins + GameData.RATE_BONUS_COINS)
                    Toast.makeText(this@MainActivity, "+${GameData.RATE_BONUS_COINS} Bonus Coins Added!", Toast.LENGTH_SHORT).show()
                }
            }
        })

        container.addView(Button(this).apply {
            text = "Sound: ${if (soundOn) "ON" else "OFF"}"
            isAllCaps = false
            setOnClickListener {
                toggleSound()
                text = "Sound: ${if (soundOn) "ON" else "OFF"}"
            }
        })

        container.addView(Button(this).apply {
            text = "Vibration: ${if (vibOn) "ON" else "OFF"}"
            isAllCaps = false
            setOnClickListener {
                toggleVib()
                text = "Vibration: ${if (vibOn) "ON" else "OFF"}"
            }
        })

        container.addView(Button(this).apply {
            text = "Trail Effect: ${if (trailOn) "ON" else "OFF"}"
            isAllCaps = false
            setOnClickListener {
                toggleTrail()
                text = "Trail Effect: ${if (trailOn) "ON" else "OFF"}"
            }
        })

        container.addView(Button(this).apply {
            text = "SECURE RESET (Score Only)"
            isAllCaps = false
            setOnClickListener {
                prefs.edit().putInt("h", 0).apply()
                highScore = 0
                gameView.configure(curLv, coins, highScore, trailOn, selFood, selBody, selFull, selHead)
                Toast.makeText(this@MainActivity, "Score Reset Successfully!", Toast.LENGTH_SHORT).show()
            }
        })

        activeModalDialog = AlertDialog.Builder(this)
            .setTitle("SETTINGS & PRIVACY POLICY")
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun exitGame() {
        dismissActiveDialog()
        AlertDialog.Builder(this)
            .setTitle("SGS CONFIRM")
            .setMessage("Exit ${GameData.GAME_NAME}? Your Progress is Safe! ID: $playerId")
            .setPositiveButton("YES") { _, _ ->
                adManager.syncFirebaseUser(
                    prefs.getString("uid", "guest_$playerId") ?: "guest_$playerId",
                    coins,
                    curLv,
                    highScore
                )
                finishAffinity()
            }
            .setNegativeButton("NO", null)
            .show()
    }

    private fun handleSystemBack() {
        if (leftDrawer.visibility == View.VISIBLE || rightDrawer.visibility == View.VISIBLE) {
            closeDrawers()
            return
        }
        activeModalDialog?.let {
            if (it.isShowing) {
                it.dismiss()
                activeModalDialog = null
                return
            }
        }
        backPressCount += 1
        if (backPressCount == 1) {
            Toast.makeText(this, "Press Back Again To Exit SGS - ID Safe", Toast.LENGTH_SHORT).show()
            uiHandler.postDelayed({ backPressCount = 0 }, 2000L)
        } else {
            backPressCount = 0
            exitGame()
        }
    }

    private fun dismissActiveDialog() {
        activeModalDialog?.dismiss()
        activeModalDialog = null
    }

    private fun createAvatarBitmap(px: Int): android.graphics.Bitmap {
        val safePx = px.coerceAtLeast(24)
        val bmp = android.graphics.Bitmap.createBitmap(safePx, safePx, android.graphics.Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.parseColor("#0A0A1A")
        c.drawRect(0f, 0f, safePx.toFloat(), safePx.toFloat(), p)
        p.color = Color.parseColor("#00FFFF")
        c.drawCircle(safePx / 2f, safePx / 2f, safePx / 3f, p)
        p.color = Color.BLACK
        p.textSize = (safePx * 0.36f).coerceAtLeast(10f)
        p.textAlign = Paint.Align.CENTER
        p.isFakeBoldText = true
        c.drawText("SGS", safePx / 2f, safePx / 2f + (p.textSize * 0.35f), p)
        return bmp
    }

    private fun GameData.FoodItem.nWithDesc(): String = "$name - $shape - $price Coin"
    private fun GameData.BodyItem.nWithDesc(): String = "$name - $pattern - $price Coin"
    private fun GameData.FullBodyItem.nWithDesc(): String = "$name - $pattern - ${size}px - $price Coin"
    private fun GameData.HeadItem.nWithDesc(): String = "Head $name $symbol - $price Coin"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onPause() {
        super.onPause()
        gameView.pauseForMenu()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacksAndMessages(null)
        adManager.destroy()
    }
}
