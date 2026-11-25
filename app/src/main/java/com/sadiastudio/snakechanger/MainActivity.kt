package com.sadiastudio.snakechanger

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var coins = GameData.START_COINS
    private var currentScreen = "lobby"

    private var selectedFood = 0
    private var selectedBody = 0
    private var selectedFull = 0
    private var selectedHead = 0
    private var selectedLevel = 0

    private var soundOn = true
    private var vibrationOn = true
    private var trailOn = true
    private var ratedClaimed = false

    private var bestScore = 0
    private var totalGames = 0
    private var playerId = ""

    private var unlockedFood = mutableSetOf(0)
    private var unlockedBody = mutableSetOf(0)
    private var unlockedFull = mutableSetOf(0)
    private var unlockedHead = mutableSetOf(0)
    private var unlockedLevels = mutableSetOf(0)

    private lateinit var rootLayout: LinearLayout
    private lateinit var coinText: TextView
    private lateinit var contentFrame: FrameLayout
    private var currentGameView: GameView? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        NotificationHelper.scheduleDailyReminder(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("SGS_SNAKE_CHANGER_PREFS", Context.MODE_PRIVATE)
        loadData()
        AdManager.initialize(this)
        setupNotificationReminder()
        setupBackHandler()
        buildUiShell()
        showLobby()
    }

    private fun setupNotificationReminder() {
        NotificationHelper.createChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.hasNotificationPermission(this)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationHelper.scheduleDailyReminder(this)
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    currentScreen == "game" -> {
                        currentGameView?.pauseGame()
                        showLobby()
                    }
                    currentScreen != "lobby" -> showLobby()
                    else -> confirmExitApp()
                }
            }
        })
    }

    private fun confirmExitApp() {
        AlertDialog.Builder(this)
            .setTitle("Exit Snake Changer - SGS")
            .setMessage("Your progress is saved. Player ID: $playerId\nExit game?")
            .setPositiveButton("Exit") { _, _ -> finishAffinity() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildUiShell() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#080810"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val bannerBar = TextView(this).apply {
            text = AdManager.getBannerStatusText()
            textSize = 11f
            setTextColor(Color.parseColor("#00FFFF"))
            setBackgroundColor(Color.parseColor("#101018"))
            gravity = Gravity.CENTER
            setPadding(12, 8, 12, 8)
        }
        rootLayout.addView(bannerBar)

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 16, 24, 14)
            setBackgroundColor(Color.parseColor("#161622"))
        }

        coinText = TextView(this).apply {
            text = "Coins: $coins"
            textSize = 18f
            setTextColor(Color.parseColor("#FFCC00"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val settingsBtn = Button(this).apply {
            text = "Settings"
            setOnClickListener { showSettingsDialog() }
        }

        topBar.addView(coinText)
        topBar.addView(settingsBtn)
        rootLayout.addView(topBar)

        contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        rootLayout.addView(contentFrame)

        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#161622"))
            setPadding(8, 8, 8, 8)
        }

        bottomNav.addView(makeNavButton("Lobby") { showLobby() })
        bottomNav.addView(makeNavButton("Shop") { showShop() })
        bottomNav.addView(makeNavButton("Levels") { showLevels() })
        bottomNav.addView(makeNavButton("Spin") { showSpin() })
        rootLayout.addView(bottomNav)

        setContentView(rootLayout)
    }

    private fun makeNavButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
        }
    }

    private fun loadIndexSet(key: String): MutableSet<Int> {
        val raw = prefs.getString(key, "0").orEmpty()
        val parsed = if (raw.isBlank()) {
            mutableSetOf(0)
        } else {
            raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableSet()
        }
        parsed.add(0)
        return parsed
    }

    private fun saveIndexSet(key: String, set: Set<Int>) {
        prefs.edit().putString(key, set.joinToString(",")).apply()
    }

    private fun generatePlayerId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val suffix = buildString {
            repeat(6) {
                append(chars[kotlin.random.Random.nextInt(chars.length)])
            }
        }
        return "SGS-$suffix"
    }

    private fun loadData() {
        coins = GameData.decodeCoins(prefs.getString("sgs_secure_c", null))
        selectedFood = prefs.getInt("selected_food", 0).coerceIn(0, GameData.foodItems.size - 1)
        selectedBody = prefs.getInt("selected_body", 0).coerceIn(0, GameData.bodyItems.size - 1)
        selectedFull = prefs.getInt("selected_full", 0).coerceIn(0, GameData.fullBodies.size - 1)
        selectedHead = prefs.getInt("selected_head", 0).coerceIn(0, GameData.heads.size - 1)
        selectedLevel = prefs.getInt("selected_level", 0).coerceIn(0, GameData.levelConfigs.size - 1)

        soundOn = prefs.getBoolean("sound_on", true)
        vibrationOn = prefs.getBoolean("vibration_on", true)
        trailOn = prefs.getBoolean("trail_on", true)
        ratedClaimed = prefs.getBoolean("rated_claimed", false)

        bestScore = prefs.getInt("best_score", 0).coerceAtLeast(0)
        totalGames = prefs.getInt("total_games", 0).coerceAtLeast(0)

        val savedId = prefs.getString("sgs_player_id", null)
        playerId = if (savedId.isNullOrBlank() || !savedId.startsWith("SGS-")) {
            val generated = generatePlayerId()
            prefs.edit().putString("sgs_player_id", generated).apply()
            generated
        } else {
            savedId
        }

        unlockedFood = loadIndexSet("unlocked_food")
        unlockedBody = loadIndexSet("unlocked_body")
        unlockedFull = loadIndexSet("unlocked_full")
        unlockedHead = loadIndexSet("unlocked_head")
        unlockedLevels = loadIndexSet("unlocked_levels")
    }

    private fun saveData() {
        coins = coins.coerceAtLeast(0)
        prefs.edit()
            .putString("sgs_secure_c", GameData.encodeCoins(coins))
            .putInt("selected_food", selectedFood)
            .putInt("selected_body", selectedBody)
            .putInt("selected_full", selectedFull)
            .putInt("selected_head", selectedHead)
            .putInt("selected_level", selectedLevel)
            .putBoolean("sound_on", soundOn)
            .putBoolean("vibration_on", vibrationOn)
            .putBoolean("trail_on", trailOn)
            .putBoolean("rated_claimed", ratedClaimed)
            .putInt("best_score", bestScore)
            .putInt("total_games", totalGames)
            .apply()

        saveIndexSet("unlocked_food", unlockedFood)
        saveIndexSet("unlocked_body", unlockedBody)
        saveIndexSet("unlocked_full", unlockedFull)
        saveIndexSet("unlocked_head", unlockedHead)
        saveIndexSet("unlocked_levels", unlockedLevels)

        if (::coinText.isInitialized) {
            coinText.text = "Coins: $coins"
        }
    }

    private fun addCoins(amount: Int, showToast: Boolean = true) {
        if (amount <= 0) return
        coins += amount
        saveData()
        if (showToast) {
            Toast.makeText(this, "+$amount Coins", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLobby() {
        currentScreen = "lobby"
        currentGameView?.pauseGame()
        currentGameView = null
        contentFrame.removeAllViews()

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 24, 28, 24)
        }

        val currentLvl = GameData.safeLevel(selectedLevel)
        val currentFood = GameData.safeFood(selectedFood)
        val currentBody = GameData.safeBody(selectedBody)
        val currentFull = GameData.safeFull(selectedFull)
        val currentHead = GameData.safeHead(selectedHead)

        layout.addView(TextView(this).apply {
            text = "SNAKE CHANGER - SGS"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        layout.addView(TextView(this).apply {
            text = "ID: $playerId | Unlocked: ${unlockedLevels.size}/15\nBest Score: $bestScore | Total Games: $totalGames"
            textSize = 13f
            setTextColor(Color.parseColor("#00FFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 14)
        })

        layout.addView(TextView(this).apply {
            text = "LOADOUT PREVIEW\nLevel: ${currentLvl.name}\nFood: ${currentFood.name} (${currentFood.shape})\nBody: ${currentBody.name} (${currentBody.pattern})\nFull: ${currentFull.name}\nHead: ${currentHead.name} [${currentHead.emoji}]"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#181826"))
            setPadding(24, 20, 24, 20)
            gravity = Gravity.CENTER
        })

        layout.addView(Button(this).apply {
            text = "PLAY NOW"
            textSize = 19f
            setPadding(0, 18, 0, 18)
            setOnClickListener { startGame() }
        })

        layout.addView(Button(this).apply {
            text = "Open Shop"
            setOnClickListener { showShop() }
        })

        layout.addView(Button(this).apply {
            text = "Select Level"
            setOnClickListener { showLevels() }
        })

        layout.addView(Button(this).apply {
            text = "Daily Spin"
            setOnClickListener { showSpin() }
        })

        scrollView.addView(layout)
        contentFrame.addView(scrollView)
    }

    private fun startGame() {
        currentScreen = "game"
        contentFrame.removeAllViews()

        val levelConfig = GameData.safeLevel(selectedLevel)
        val gameView = GameView(
            context = this,
            levelIndex = selectedLevel,
            level = levelConfig,
            foodIndexRaw = selectedFood,
            bodyIndexRaw = selectedBody,
            fullIndexRaw = selectedFull,
            headIndexRaw = selectedHead,
            soundOn = soundOn,
            vibrationOn = vibrationOn,
            trailOn = trailOn,
            onScore = { finalScore, earned ->
                runOnUiThread {
                    totalGames++
                    if (finalScore > bestScore) {
                        bestScore = finalScore
                    }
                    if (earned > 0) {
                        coins += earned
                    }
                    saveData()
                    AdManager.onGameOver(this) {
                        Toast.makeText(
                            this,
                            "Game Over! Score: $finalScore | Earned: +$earned Coins",
                            Toast.LENGTH_LONG
                        ).show()
                        showLobby()
                    }
                }
            },
            onExit = { showLobby() }
        )

        currentGameView = gameView
        contentFrame.addView(gameView)
    }

    private fun showShop() {
        currentScreen = "shop"
        currentGameView?.pauseGame()
        currentGameView = null
        contentFrame.removeAllViews()

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 24)
        }

        layout.addView(TextView(this).apply {
            text = "SHOP - Balance: $coins Coins"
            textSize = 19f
            setTextColor(Color.parseColor("#FFCC00"))
            setTypeface(null, Typeface.BOLD)
        })

        fun addSectionTitle(title: String) {
            layout.addView(TextView(this).apply {
                text = title
                textSize = 16f
                setTextColor(Color.parseColor("#00FFFF"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 20, 0, 8)
            })
        }

        fun handleUnlockOrSelect(
            index: Int,
            price: Int,
            unlockedSet: MutableSet<Int>,
            onSelect: () -> Unit
        ) {
            if (unlockedSet.contains(index)) {
                onSelect()
                saveData()
                showShop()
                Toast.makeText(this, "Equipped", Toast.LENGTH_SHORT).show()
            } else if (coins >= price) {
                coins -= price
                unlockedSet.add(index)
                onSelect()
                saveData()
                showShop()
                Toast.makeText(this, "Unlocked (-$price Coins)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Need $price Coins - Spin for Coins", Toast.LENGTH_SHORT).show()
            }
        }

        addSectionTitle("Food Shapes (15)")
        GameData.foodItems.forEachIndexed { idx, item ->
            val owned = unlockedFood.contains(idx)
            val label = if (owned) "Owned" else "${item.price} Coins"
            layout.addView(makeItemRow("${item.name} - ${item.shape} ($label)", idx == selectedFood, owned) {
                handleUnlockOrSelect(idx, item.price, unlockedFood) { selectedFood = idx }
            })
        }

        addSectionTitle("Body Patterns (15)")
        GameData.bodyItems.forEachIndexed { idx, item ->
            val owned = unlockedBody.contains(idx)
            val label = if (owned) "Owned" else "${item.price} Coins"
            layout.addView(makeItemRow("${item.name} - ${item.pattern} ($label)", idx == selectedBody, owned) {
                handleUnlockOrSelect(idx, item.price, unlockedBody) { selectedBody = idx }
            })
        }

        addSectionTitle("Full Compact Bodies (15)")
        GameData.fullBodies.forEachIndexed { idx, item ->
            val owned = unlockedFull.contains(idx)
            val label = if (owned) "Owned" else "${item.price} Coins"
            layout.addView(makeItemRow("${item.name} - ${item.size}px ($label)", idx == selectedFull, owned) {
                handleUnlockOrSelect(idx, item.price, unlockedFull) { selectedFull = idx }
            })
        }

        addSectionTitle("Head Characters (10)")
        GameData.heads.forEachIndexed { idx, item ->
            val owned = unlockedHead.contains(idx)
            val label = if (owned) "Owned" else "${item.price} Coins"
            layout.addView(makeItemRow("${item.name} [${item.emoji}] ($label)", idx == selectedHead, owned) {
                handleUnlockOrSelect(idx, item.price, unlockedHead) { selectedHead = idx }
            })
        }

        layout.addView(Button(this).apply {
            text = "Back to Lobby"
            setOnClickListener { showLobby() }
        })

        scrollView.addView(layout)
        contentFrame.addView(scrollView)
    }

    private fun makeItemRow(title: String, isEquipped: Boolean, isOwned: Boolean, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6, 6, 6, 6)
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 13f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(this@MainActivity).apply {
                text = when {
                    isEquipped -> "Equipped"
                    isOwned -> "Equip"
                    else -> "Unlock"
                }
                setOnClickListener { onClick() }
            })
        }
    }

    private fun showLevels() {
        currentScreen = "levels"
        currentGameView?.pauseGame()
        currentGameView = null
        contentFrame.removeAllViews()

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 24)
        }

        layout.addView(TextView(this).apply {
            text = "LEVELS (15)"
            textSize = 19f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })

        GameData.levelConfigs.forEachIndexed { idx, cfg ->
            val cost = GameData.levelCosts.getOrElse(idx) { 0 }
            val unlocked = unlockedLevels.contains(idx) || idx == 0

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(6, 8, 6, 8)
            }

            row.addView(TextView(this).apply {
                val stateText = if (unlocked) "Unlocked" else "Cost: $cost Coins"
                text = "${cfg.name}\n$stateText"
                textSize = 13f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })

            row.addView(Button(this).apply {
                text = when {
                    idx == selectedLevel -> "Playing"
                    unlocked -> "Play"
                    else -> "Unlock ($cost)"
                }
                setOnClickListener {
                    if (unlocked) {
                        selectedLevel = idx
                        saveData()
                        startGame()
                    } else if (coins >= cost) {
                        coins -= cost
                        unlockedLevels.add(idx)
                        selectedLevel = idx
                        saveData()
                        Toast.makeText(this@MainActivity, "Level ${idx + 1} Unlocked", Toast.LENGTH_SHORT).show()
                        startGame()
                    } else {
                        Toast.makeText(this@MainActivity, "Need $cost Coins", Toast.LENGTH_SHORT).show()
                    }
                }
            })

            layout.addView(row)
        }

        layout.addView(Button(this).apply {
            text = "Back to Lobby"
            setOnClickListener { showLobby() }
        })

        scrollView.addView(layout)
        contentFrame.addView(scrollView)
    }

    private fun showSpin() {
        currentScreen = "spin"
        currentGameView?.pauseGame()
        currentGameView = null
        contentFrame.removeAllViews()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        layout.addView(TextView(this).apply {
            text = "SPIN WHEEL"
            textSize = 24f
            setTextColor(Color.parseColor("#FFCC00"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        layout.addView(TextView(this).apply {
            text = "Rewards: 1500 / 2000 / 3000 Coins"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 24)
        })

        layout.addView(Button(this).apply {
            text = "WATCH AD + SPIN"
            textSize = 18f
            setOnClickListener {
                AdManager.showRewardedInterstitial(
                    activity = this@MainActivity,
                    title = "Spin Wheel Rewarded Ad",
                    onRewarded = {
                        val reward = GameData.spinRewards.random()
                        addCoins(reward)
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Spin Reward")
                            .setMessage("You won $reward Coins in ${GameData.GAME_NAME}!")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                )
            }
        })

        layout.addView(Button(this).apply {
            text = "Back to Lobby"
            setOnClickListener { showLobby() }
        })

        contentFrame.addView(layout)
    }

    private fun showSettingsDialog() {
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 16)
        }

        dialogLayout.addView(TextView(this).apply {
            text = "Player ID: $playerId | Best: $bestScore"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        })

        dialogLayout.addView(Switch(this).apply {
            text = "Sound Effects"
            isChecked = soundOn
            setOnCheckedChangeListener { _, checked ->
                soundOn = checked
                saveData()
            }
        })

        dialogLayout.addView(Switch(this).apply {
            text = "Vibration"
            isChecked = vibrationOn
            setOnCheckedChangeListener { _, checked ->
                vibrationOn = checked
                saveData()
            }
        })

        dialogLayout.addView(Switch(this).apply {
            text = "Trail Effect"
            isChecked = trailOn
            setOnCheckedChangeListener { _, checked ->
                trailOn = checked
                saveData()
            }
        })

        dialogLayout.addView(Button(this).apply {
            text = if (ratedClaimed) "Rating Recorded" else "Rate Us (+${GameData.RATE_BONUS_COINS} Coins)"
            setOnClickListener {
                if (!ratedClaimed) {
                    ratedClaimed = true
                    addCoins(GameData.RATE_BONUS_COINS)
                } else {
                    Toast.makeText(this@MainActivity, "Thank you for rating!", Toast.LENGTH_SHORT).show()
                }
            }
        })

        dialogLayout.addView(Button(this).apply {
            text = "Reset Best Score"
            setOnClickListener {
                bestScore = 0
                saveData()
                Toast.makeText(this@MainActivity, "Best score reset. Coins & unlocks kept safe.", Toast.LENGTH_SHORT).show()
                if (currentScreen == "lobby") showLobby()
            }
        })

        dialogLayout.addView(Button(this).apply {
            text = "Privacy Policy"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Privacy Policy - ${GameData.GAME_NAME}")
                    .setMessage(
                        "Studio: ${GameData.STUDIO}\n" +
                        "Developer: ${GameData.DEVELOPER}\n" +
                        "Rating: Everyone 3+\n" +
                        "We do not collect personal data. Coins, high scores, and unlocks are stored locally on your device."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        })

        dialogLayout.addView(Button(this).apply {
            text = "Exit Game"
            setOnClickListener { confirmExitApp() }
        })

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(dialogLayout)
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        currentGameView?.pauseGame()
    }

    override fun onResume() {
        super.onResume()
        if (currentScreen == "game") {
            currentGameView?.resumeGame()
        }
    }
}
