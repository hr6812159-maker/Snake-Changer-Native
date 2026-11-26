package com.sadiastudio.snakechanger

import android.util.Base64

object GameData {

    const val GAME_NAME = "SNAKE CHANGER"
    const val BRAND = "Snake Changer - SGS"
    const val STUDIO = "Sadis Studio"
    const val DEVELOPER = "Habibur Rahman"
    const val PACKAGE_NAME = "com.sadiastudio.snakechanger"
    const val UPTODOWN_URL = "https://snake-changer-sgs.en.uptodown.com/android"

    const val MONETAG_BASE_URL = "https://hr6812159-maker.github.io"
    const val MONETAG_META_TAG = "monetag"
    const val MONETAG_SCRIPT_SRC = "https://nap5k.com/tag.min.js"
    const val SPIN_REWARD_ZONE_ID = "12205081"

    const val START_COINS = 5
    const val FOODS_PER_POWER_COIN = 10
    const val POWER_COINS_PER_10_FOOD = 1
    const val SPECIAL_COIN_REWARD = 2
    const val RATE_BONUS_COINS = 15

    val spinCoinRewards = intArrayOf(15, 25, 40)

    val levelCosts = intArrayOf(
        0,    // Lv 1 (Unlocked by default)
        10,   // Lv 2
        20,   // Lv 3
        30,   // Lv 4
        45,   // Lv 5
        60,   // Lv 6
        80,   // Lv 7
        100,  // Lv 8
        125,  // Lv 9
        150,  // Lv 10
        180,  // Lv 11
        210,  // Lv 12
        245,  // Lv 13
        280,  // Lv 14
        320   // Lv 15
    )

    data class HeadItem(
        val name: String,
        val emoji: String,
        val colorHex: String
    )

    data class BodyItem(
        val name: String,
        val colorHex: String
    )

    data class FullBodyItem(
        val name: String,
        val colorHex: String,
        val centerHex: String
    )

    data class FoodItem(
        val name: String,
        val shape: String,
        val colorHex: String
    )

    data class LevelConfig(
        val level: Int,
        val name: String,
        val bgHex: String,
        val trailHex: String,
        val speedBonus: Int,
        val head: Int,
        val body: Int,
        val full: Int,
        val food: Int
    )

    val heads = listOf(
        HeadItem("Cyber Viper", "V", "#00ffff"),
        HeadItem("Neon Cobra", "C", "#00ff88"),
        HeadItem("Solar Fang", "S", "#ffcc00"),
        HeadItem("Plasma Drake", "D", "#ff55aa")
    )

    val bodyItems = listOf(
        BodyItem("Cyan Pulse", "#00bcd4"),
        BodyItem("Emerald Scale", "#00e676"),
        BodyItem("Gold Link", "#ffd54f"),
        BodyItem("Violet Core", "#b388ff")
    )

    val fullBodies = listOf(
        FullBodyItem("Matrix Ring", "#00e5ff", "#0a192f"),
        FullBodyItem("Venom Node", "#69f0ae", "#0b2e1d"),
        FullBodyItem("Amber Core", "#ffca28", "#2e2100"),
        FullBodyItem("Nova Orb", "#ea80fc", "#280b30")
    )

    val foodItems = listOf(
        FoodItem("Energy Orb", "circle", "#ff4081"),
        FoodItem("Prism Cube", "square", "#40c4ff"),
        FoodItem("Diamond Gem", "diamond", "#ffd740"),
        FoodItem("Delta Shard", "triangle", "#69f0ae"),
        FoodItem("Star Core", "star", "#ff6e40")
    )

    val levels = listOf(
        LevelConfig(1, "Neon Grid", "#0a0d16", "#00ffff", 0, 0, 0, 0, 0),
        LevelConfig(2, "Emerald Circuit", "#07140e", "#00ff88", 3, 1, 1, 1, 3),
        LevelConfig(3, "Golden Pulse", "#161106", "#ffcc00", 6, 2, 2, 2, 2),
        LevelConfig(4, "Violet Matrix", "#12081a", "#ea80fc", 9, 3, 3, 3, 4),
        LevelConfig(5, "Ice Breaker", "#061521", "#40c4ff", 12, 0, 0, 1, 1),
        LevelConfig(6, "Crimson Core", "#1a070d", "#ff4081", 15, 3, 2, 3, 0),
        LevelConfig(7, "Turbo Track", "#0b111e", "#00e5ff", 18, 1, 0, 2, 2),
        LevelConfig(8, "Solar Flare", "#1c1205", "#ffab40", 21, 2, 2, 1, 4),
        LevelConfig(9, "Shadow Node", "#0d0b14", "#b388ff", 24, 3, 3, 0, 1),
        LevelConfig(10, "Hyper Venom", "#061912", "#1de9b6", 27, 1, 1, 2, 3),
        LevelConfig(11, "Plasma Surge", "#18091c", "#ff55aa", 30, 3, 1, 3, 2),
        LevelConfig(12, "Titanium Rush", "#09131c", "#80d8ff", 33, 0, 2, 0, 0),
        LevelConfig(13, "Nova Storm", "#1a0d06", "#ff6e40", 36, 2, 3, 2, 4),
        LevelConfig(14, "Quantum Drift", "#08171a", "#00ffff", 39, 1, 2, 1, 1),
        LevelConfig(15, "Omega Overdrive", "#140712", "#ff4081", 42, 3, 3, 3, 4)
    )

    fun safeLevel(level: Int): LevelConfig {
        val idx = (level.coerceIn(1, 15)) - 1
        return levels.getOrElse(idx) { levels.first() }
    }

    fun encodeCoins(coins: Int): String {
        val safe = coins.coerceAtLeast(0)
        val raw = "SGS_PC:$safe:${safe * 7 + 13}"
        return Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun decodeCoins(encoded: String?): Int {
        if (encoded.isNullOrBlank()) return START_COINS
        return try {
            val decoded = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
            val parts = decoded.split(":")
            if (parts.size >= 3 && parts[0] == "SGS_PC") {
                val value = parts[1].toIntOrNull() ?: return START_COINS
                val check = parts[2].toIntOrNull() ?: return START_COINS
                if (value >= 0 && check == value * 7 + 13) value else START_COINS
            } else {
                START_COINS
            }
        } catch (_: Exception) {
            START_COINS
        }
    }
}
