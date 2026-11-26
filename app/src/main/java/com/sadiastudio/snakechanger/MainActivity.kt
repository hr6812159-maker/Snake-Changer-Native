package com.sadiastudio.snakechanger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private lateinit var notificationHelper: NotificationHelper

    private lateinit var tvCoins: TextView
    private lateinit var tvScore: TextView
    private lateinit var tvBest: TextView
    private lateinit var tvLevel: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvPlayerId: TextView

    private val uiHandler = Handler(Looper.getMainLooper())
    private var lastBackPressTime = 0L
    private var activeOverlayDialog: AlertDialog? = null

    private val prefs by lazy {
        getSharedPreferences("sgs_native_prefs", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationHelper = NotificationHelper(this)
        notificationHelper.createChannelIfNeeded()
        requestNotificationPermissionIfNeeded()
        notificationHelper.schedule24HourReminder()

        val playerId = getOrCreatePlayerId()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#080810"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Fixed Top Banner Slot (no overlap)
        val bannerSlot = FrameLayout(this).apply {
            id = View.generateViewId()
            setBackgroundColor(Color.parseColor("#11111a"))
            background = makeBoxDrawable("#11111a", "#00ffff", dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                bottomMargin = dp(8)
            }
        }
        val bannerLabel = TextView(this).apply {
            text = "TOP BANNER AD SLOT (${GameData.BRAND})"
            setTextColor(Color.parseColor("#00ffff"))
            textSize = 10f
            gravity = Gravity.CENTER
        }
        bannerSlot.addView(
            bannerLabel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        rootLayout.addView(bannerSlot)

        // 2. Player & Level Info Bar
        val playerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = makeBoxDrawable("#11111e", "#00ffff", dp(10))
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
        }
        tvPlayerId = TextView(this).apply {
            text = "ID: $playerId"
            setTextColor(Color.parseColor("#00ffff"))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvLevel = TextView(this).apply {
            text = "Lv ${prefs.getInt("cur_lv", 1)}/15"
            setTextColor(Color.BLACK)
            textSize = 11f
            setBackgroundColor(Color.parseColor("#00ffff"))
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }
        playerBar.addView(tvPlayerId)
        playerBar.addView(tvLevel)
        rootLayout.addView(playerBar)

        // 3. Game HUD Strip (Power Coin, Score, Level Best, Speed)
        val hudBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = makeBoxDrawable("#141422", "#00ff88", dp(8))
            setPadding(dp(8), dp(5), dp(8), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
        }
        tvCoins = TextView(this).apply {
            text = "PC: ${GameData.START_COINS}"
            setTextColor(Color.parseColor("#00ff88"))
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvScore = TextView(this).apply {
            text = "SC: 0"
            setTextColor(Color.parseColor("#ffeb3b"))
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvBest = TextView(this).apply {
            text = "BEST: 0"
            setTextColor(Color.parseColor("#00ffff"))
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvSpeed = TextView(this).apply {
            text = "SPD: 196ms"
            setTextColor(Color.WHITE)
            textSize = 10f
            gravity = Gravity.END
        }
        hudBar.addView(tvCoins)
        hudBar.addView(tvScore)
        hudBar.addView(tvBest)
        hudBar.addView(tvSpeed)
        rootLayout.addView(hudBar)

        // 4. Native GameView
        gameView = GameView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                bottomMargin = dp(8)
            }
            onHudUpdate = { snap ->
                tvCoins.text = "PC: ${snap.coins}"
                tvScore.text = "SC: ${snap.score}"
                tvBest.text = "BEST: ${snap.bestScore}"
                tvLevel.text = "Lv ${snap.level}/15"
                tvSpeed.text = "SPD: ${snap.speedMs}ms"
            }
        }
        rootLayout.addView(gameView)

        // 5. Quick Native Action Row (Levels, Spin, Settings, Exit)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }

        val btnLevels = makeActionButton("LEVELS") {
            gameView.pauseForOverlay()
            showLevelsDialog()
        }
        val btnSpin = makeActionButton("SPIN +PC") {
            gameView.pauseForOverlay()
            showSpinRewardDialog()
        }
        val btnSettings = makeActionButton("SETTINGS") {
            gameView.pauseForOverlay()
            showSettingsDialog()
        }
        val btnExit = makeActionButton("EXIT") {
            confirmExitApp()
        }

        actionRow.addView(btnLevels)
        actionRow.addView(btnSpin)
        actionRow.addView(btnSettings)
        actionRow.addView(btnExit)
        rootLayout.addView(actionRow)

        // 6. D-Pad Controls
        val dpadGrid = GridLayout(this).apply {
            columnCount = 3
            rowCount = 3
            alignmentMode = GridLayout.ALIGN_BOUNDS
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(4)
            }
        }

        dpadGrid.addView(makeEmptySpacer(), gridSpec(0, 0))
        dpadGrid.addView(makeDpadButton("↑") { gameView.setDirection(GameView.Direction.UP) }, gridSpec(0, 1))
        dpadGrid.addView(makeEmptySpacer(), gridSpec(0, 2))

        dpadGrid.addView(makeDpadButton("←") { gameView.setDirection(GameView.Direction.LEFT) }, gridSpec(1, 0))
        dpadGrid.addView(makeDpadButton("OK") { gameView.togglePause() }, gridSpec(1, 1))
        dpadGrid.addView(makeDpadButton("→") { gameView.setDirection(GameView.Direction.RIGHT) }, gridSpec(1, 2))

        dpadGrid.addView(makeEmptySpacer(), gridSpec(2, 0))
        dpadGrid.addView(makeDpadButton("↓") { gameView.setDirection(GameView.Direction.DOWN) }, gridSpec(2, 1))
        dpadGrid.addView(makeEmptySpacer(), gridSpec(2, 2))

        rootLayout.addView(dpadGrid)

        setContentView(rootLayout)

        tvBest.text = "BEST: ${gameView.getLevelBestScore(gameView.getCurrentLevel())}"
        tvCoins.text = "PC: ${gameView.getCoins()}"
        tvLevel.text = "Lv ${gameView.getCurrentLevel()}/15"
        tvSpeed.text = "SPD: ${gameView.getSpeedMs()}ms"

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val dlg = activeOverlayDialog
                if (dlg != null && dlg.isShowing) {
                    dlg.dismiss()
                    activeOverlayDialog = null
                    return
                }
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2200L) {
                    finishAffinity()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(
                        this@MainActivity,
                        "Press BACK again to exit ${GameData.GAME_NAME}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    private fun showLevelsDialog() {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        scroll.addView(container)

        val dialog = AlertDialog.Builder(this)
            .setTitle("15 LEVELS • LEVEL BEST SCORES")
            .setView(scroll)
            .setNegativeButton("CLOSE", null)
            .create()

        for (lv in 1..15) {
            val cfg = GameData.safeLevel(lv)
            val cost = GameData.levelCosts.getOrElse(lv - 1) { 0 }
            val unlocked = lv == 1 || prefs.getBoolean("lv_unlocked_$lv", false)
            val best = gameView.getLevelBestScore(lv)

            val rowBtn = Button(this).apply {
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                setPadding(dp(10), dp(8), dp(10), dp(8))
                text = if (unlocked) {
                    "Lv$lv: ${cfg.name} | Best: $best | PLAY"
                } else {
                    "Lv$lv: LOCKED ($cost PC) | Best: $best | UNLOCK"
                }
                setOnClickListener {
                    if (unlocked) {
                        gameView.selectLevel(lv)
                        dialog.dismiss()
                    } else if (gameView.getCoins() >= cost) {
                        gameView.addCoins(-cost)
                        prefs.edit().putBoolean("lv_unlocked_$lv", true).apply()
                        gameView.selectLevel(lv)
                        Toast.makeText(this@MainActivity, "Lv$lv Unlocked!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Need $cost PC (Have ${gameView.getCoins()} PC)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            container.addView(rowBtn)
        }

        activeOverlayDialog = dialog
        dialog.setOnDismissListener {
            if (activeOverlayDialog === dialog) activeOverlayDialog = null
        }
        dialog.show()
    }

    private fun showSpinRewardDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("SPIN WHEEL (+15 / +25 / +40 PC)")
            .setMessage("Watch reward ad to spin for Power Coins. Reward is granted only after ad completes.")
            .setPositiveButton("WATCH AD & SPIN") { _, _ ->
                val progressDlg = AlertDialog.Builder(this)
                    .setTitle("REWARD AD PLAYING")
                    .setMessage("Please wait for ad completion...")
                    .setCancelable(false)
                    .create()
                progressDlg.show()
                activeOverlayDialog = progressDlg

                uiHandler.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        progressDlg.dismiss()
                        activeOverlayDialog = null
                        val reward = GameData.spinCoinRewards[Random.nextInt(GameData.spinCoinRewards.size)]
                        gameView.addCoins(reward)
                        Toast.makeText(
                            this@MainActivity,
                            "Ad Completed! +$reward Power Coins added.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }, 1500L)
            }
            .setNegativeButton("CANCEL", null)
            .create()

        activeOverlayDialog = dialog
        dialog.setOnDismissListener {
            if (activeOverlayDialog === dialog) activeOverlayDialog = null
        }
        dialog.show()
    }

    private fun showSettingsDialog() {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        scroll.addView(box)

        val btnSound = Button(this).apply {
            text = "Sound: ${if (gameView.isSoundEnabled()) "ON" else "OFF"}"
            setOnClickListener {
                val next = !gameView.isSoundEnabled()
                gameView.setSoundEnabled(next)
                text = "Sound: ${if (next) "ON" else "OFF"}"
            }
        }

        val btnVib = Button(this).apply {
            text = "Vibration: ${if (gameView.isVibrationEnabled()) "ON" else "OFF"}"
            setOnClickListener {
                val next = !gameView.isVibrationEnabled()
                gameView.setVibrationEnabled(next)
                text = "Vibration: ${if (next) "ON" else "OFF"}"
            }
        }

        val btnTrail = Button(this).apply {
            text = "Trail Effect: ${if (gameView.isTrailEnabled()) "ON" else "OFF"}"
            setOnClickListener {
                val next = !gameView.isTrailEnabled()
                gameView.setTrailEnabled(next)
                text = "Trail Effect: ${if (next) "ON" else "OFF"}"
            }
        }

        val btnReminder = Button(this).apply {
            text = "24h Reminder: ${if (notificationHelper.isReminderEnabled()) "ON" else "OFF"}"
            setOnClickListener {
                val next = !notificationHelper.isReminderEnabled()
                notificationHelper.setReminderEnabled(next)
                text = "24h Reminder: ${if (next) "ON" else "OFF"}"
                Toast.makeText(
                    this@MainActivity,
                    if (next) "24h Reminder ON" else "24h Reminder OFF",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val presetLabel = TextView(this).apply {
            text = "Model Preset:"
            setTextColor(Color.CYAN)
            textSize = 11f
            setPadding(0, dp(8), 0, dp(4))
        }

        val presetScroll = HorizontalScrollView(this)
        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        presetScroll.addView(presetRow)

        listOf("AUTO", "CLASSIC", "COMPACT", "TURBO").forEach { mode ->
            val pBtn = Button(this).apply {
                text = mode
                setOnClickListener {
                    applyPreset(mode)
                    Toast.makeText(this@MainActivity, "Preset: $mode", Toast.LENGTH_SHORT).show()
                }
            }
            presetRow.addView(pBtn)
        }

        val btnResetBest = Button(this).apply {
            text = "Reset Lv${gameView.getCurrentLevel()} Best Score"
            setOnClickListener {
                gameView.resetCurrentLevelBestScore()
                Toast.makeText(this@MainActivity, "Level Best Score Reset", Toast.LENGTH_SHORT).show()
            }
        }

        val btnRate = Button(this).apply {
            text = "Rate on Uptodown (+${GameData.RATE_BONUS_COINS} PC)"
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GameData.UPTODOWN_URL)))
                } catch (_: Exception) {
                }
                if (!prefs.getBoolean("rate_bonus_claimed", false)) {
                    prefs.edit().putBoolean("rate_bonus_claimed", true).apply()
                    gameView.addCoins(GameData.RATE_BONUS_COINS)
                }
            }
        }

        val infoText = TextView(this).apply {
            text = "${GameData.GAME_NAME} (${GameData.BRAND}) • ${GameData.STUDIO}\nDev: ${GameData.DEVELOPER} • 10 Foods = 1 PC"
            setTextColor(Color.LTGRAY)
            textSize = 10f
            setPadding(0, dp(8), 0, 0)
        }

        box.addView(btnSound)
        box.addView(btnVib)
        box.addView(btnTrail)
        box.addView(btnReminder)
        box.addView(presetLabel)
        box.addView(presetScroll)
        box.addView(btnResetBest)
        box.addView(btnRate)
        box.addView(infoText)

        val dialog = AlertDialog.Builder(this)
            .setTitle("SETTINGS & OPTIONS")
            .setView(scroll)
            .setPositiveButton("DONE", null)
            .create()

        activeOverlayDialog = dialog
        dialog.setOnDismissListener {
            if (activeOverlayDialog === dialog) activeOverlayDialog = null
        }
        dialog.show()
    }

    private fun applyPreset(mode: String) {
        prefs.edit().putString("model_preset", mode).apply()
        when (mode) {
            "CLASSIC" -> gameView.setTrailEnabled(false)
            "COMPACT" -> gameView.setTrailEnabled(true)
            "TURBO" -> {
                gameView.setTrailEnabled(true)
                gameView.setSoundEnabled(true)
            }
            else -> gameView.setTrailEnabled(true)
        }
    }

    private fun confirmExitApp() {
        gameView.pauseForOverlay()
        val dialog = AlertDialog.Builder(this)
            .setTitle("EXIT ${GameData.GAME_NAME.uppercase()}")
            .setMessage("Your Power Coins, Level Best Scores, and 24h reminder settings are saved.")
            .setPositiveButton("EXIT") { _, _ -> finishAffinity() }
            .setNegativeButton("STAY", null)
            .create()
        activeOverlayDialog = dialog
        dialog.setOnDismissListener {
            if (activeOverlayDialog === dialog) activeOverlayDialog = null
        }
        dialog.show()
    }

    private fun getOrCreatePlayerId(): String {
        val existing = prefs.getString("sgs_player_id", null)
        if (!existing.isNullOrBlank() && existing.startsWith("SGS-")) {
            return existing
        }
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val suffix = buildString {
            repeat(6) { append(chars[Random.nextInt(chars.length)]) }
        }
        val created = "SGS-$suffix"
        prefs.edit().putString("sgs_player_id", created).apply()
        return created
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    3001
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationHelper.schedule24HourReminder()
    }

    override fun onPause() {
        super.onPause()
        gameView.pauseForOverlay()
        notificationHelper.schedule24HourReminder()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacksAndMessages(null)
    }

    private fun makeActionButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 9f
            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(38),
                1f
            ).apply {
                marginEnd = dp(3)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun makeDpadButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 13f
            layoutParams = GridLayout.LayoutParams().apply {
                width = dp(64)
                height = dp(44)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun makeEmptySpacer(): View {
        return View(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = dp(64)
                height = dp(44)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        }
    }

    private fun gridSpec(row: Int, col: Int): GridLayout.LayoutParams {
        return GridLayout.LayoutParams(
            GridLayout.spec(row, 1f),
            GridLayout.spec(col, 1f)
        )
    }

    private fun makeBoxDrawable(fillHex: String, strokeHex: String, radiusPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            setStroke(dp(2), Color.parseColor(strokeHex))
            cornerRadius = radiusPx.toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
