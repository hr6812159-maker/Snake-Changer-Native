package com.sadiastudio.snakechanger

import android.util.Base64

data class FoodItem(val name: String, val colorHex: String, val shape: String, val price: Int)
data class BodyItem(val name: String, val colorHex: String, val pattern: String, val price: Int)
data class FullBodyItem(
    val name: String,
    val dotHex: String,
    val colorHex: String,
    val centerHex: String,
    val pattern: String,
    val size: Float,
    val price: Int
)
data class HeadItem(val name: String, val emoji: String, val colorHex: String, val price: Int)
data class LevelConfig(
    val food: Int,
    val body: Int,
    val full: Int,
    val head: Int,
    val bgHex: String,
    val trailHex: String,
    val speedBonus: Int,
    val name: String
)

object GameData {
    const val PACKAGE_NAME = "com.sadiastudio.snakechanger"
    const val GAME_NAME = "Snake Changer"
    const val BRAND = "SGS"
    const val STUDIO = "Sadia Studio"
    const val DEVELOPER = "Habibur Rahman"
    const val VERSION_NAME = "1.0.0"

    const val START_COINS = 500
    const val FOOD_EARN = 50
    const val SPECIAL_COIN_EARN = 150
    const val RATE_BONUS_COINS = 100

    // Original HTML monetization & verification config
    const val MONETAG_META_TOKEN = "0f4b2c80eba77b6380a164a6194adf36"
    const val BANNER_ZONE_ID = "12205061"
    const val BANNER_SCRIPT_URL = "https://nap5k.com/tag.min.js"
    const val REWARDED_ZONE_ID = "12205081"
    const val REWARDED_SCRIPT_URL = "https://n6wxm.com/vignette.min.js"
    const val UPTODOWN_URL = "https://www.uptodown.com/"

    val spinRewards = listOf(1500, 2000, 3000, 1500, 2000, 3000)

    val foodItems = listOf(
        FoodItem("Red Apple Round", "#ff1144", "apple", 100),
        FoodItem("Yellow Star", "#ffeb3b", "star", 150),
        FoodItem("Purple Diamond", "#aa00ff", "diamond", 200),
        FoodItem("Cyan Heart", "#00e5ff", "heart", 250),
        FoodItem("Green Triangle", "#00ff88", "triangle", 300),
        FoodItem("Pink Square", "#ff88cc", "square", 350),
        FoodItem("Orange Hexagon", "#ff9900", "hexagon", 400),
        FoodItem("White Moon", "#ffffff", "moon", 450),
        FoodItem("Blue Drop", "#0099ff", "drop", 500),
        FoodItem("Lime Cross", "#aaff00", "cross", 550),
        FoodItem("Gold Crown Food", "#ffcc00", "crown", 600),
        FoodItem("Red Chilli", "#ff3300", "chilli", 650),
        FoodItem("Blue Berry", "#00aaff", "berry", 700),
        FoodItem("Yellow Lemon", "#ffff00", "lemon", 750),
        FoodItem("Rainbow Mix", "#ff00ff", "rainbow", 800)
    )

    val bodyItems = listOf(
        BodyItem("Solid Red", "#ff3300", "solid", 100),
        BodyItem("Striped Yellow Black", "#ffeb3b", "striped", 150),
        BodyItem("Dotted White", "#ffffff", "dotted", 200),
        BodyItem("Gradient Cyan Blue", "#00e5ff", "gradient", 250),
        BodyItem("Neon Glow Pink", "#ff88cc", "neon", 300),
        BodyItem("Checker Green", "#00ff88", "checker", 350),
        BodyItem("Ring Hollow Yellow", "#ffcc00", "ring", 400),
        BodyItem("Wave Line Orange", "#ff9900", "wave", 450),
        BodyItem("Zigzag Purple", "#aa00ff", "zigzag", 500),
        BodyItem("Scales Pattern", "#00ffcc", "scales", 550),
        BodyItem("Camo Pattern", "#55aa55", "camo", 600),
        BodyItem("Fire Gradient", "#ff4400", "fire", 650),
        BodyItem("Ice Blue Glow", "#00aaff", "ice", 700),
        BodyItem("Galaxy Dot", "#5500ff", "galaxy", 750),
        BodyItem("Rainbow Strip Final", "#ff00ff", "rainbow", 800)
    )

    val fullBodies = listOf(
        FullBodyItem("Neon Dot Ring Pink", "#ff88cc", "#ff88cc", "#ffffff", "dotRing", 3.8f, 150),
        FullBodyItem("Yellow 4 Micro Dot", "#ffffaa", "#ffcc00", "#ffffaa", "dotMicro4", 3.5f, 200),
        FullBodyItem("White Small Star 5 Kona", "#ffffff", "#ffffff", "#ffffaa", "smallStar", 3.2f, 250),
        FullBodyItem("Red Glow Dot Shadow", "#ff1144", "#ff1144", "#ffff00", "glowDot", 4.0f, 300),
        FullBodyItem("White Orange Center Dot", "#ffffff", "#ffffff", "#ff9900", "dotCenter", 3.6f, 350),
        FullBodyItem("Red Big Glow Yellow Center", "#ff0000", "#ff0000", "#ffeb3b", "bigGlow", 4.2f, 400),
        FullBodyItem("Yellow Ring Hollow", "#ffcc00", "#ffcc00", "#ff6600", "ring", 3.8f, 450),
        FullBodyItem("White Ball Micro Dot Small", "#ffffff", "#ffffff", "#ffffaa", "ballMicro", 3.4f, 500),
        FullBodyItem("Orange Dot Solid", "#ff6600", "#ff6600", "#ff0000", "dot", 4.0f, 550),
        FullBodyItem("Cyan Diamond Star Unique", "#00ffff", "#00ffff", "#ffffff", "diamondStar", 3.3f, 600),
        FullBodyItem("Pink Ring Big Thick", "#ff55aa", "#ff55aa", "#ffff00", "ringBig", 4.1f, 650),
        FullBodyItem("Yellow Brown Center Dot", "#ffeb3b", "#ffeb3b", "#553300", "dotCenter", 4.3f, 700),
        FullBodyItem("White Pink 3 Dot Star", "#ffffff", "#ffffff", "#ff88cc", "starPink", 3.5f, 750),
        FullBodyItem("White Small Dot Minimal", "#ffffee", "#ffffee", "#ffff00", "dotSmall", 3.2f, 800),
        FullBodyItem("Final Pink Yellow Double Glow Border", "#ff1493", "#ff1493", "#ffcc00", "finalGlow", 4.5f, 900)
    )

    val heads = listOf(
        HeadItem("Normal Round Yellow", "@", "#ffeb3b", 100),
        HeadItem("Smile Cyan", "C", "#00ffff", 150),
        HeadItem("Sleep Closed White", "-", "#ffffaa", 200),
        HeadItem("Eyes Big OO White", "OO", "#ffffff", 250),
        HeadItem("Angry Red ><", "><", "#ff3300", 300),
        HeadItem("Star Yellow *", "*", "#ffeb3b", 350),
        HeadItem("Heart Red H", "H", "#ff1144", 400),
        HeadItem("SGS Logo Cyan SGS", "SGS", "#00ffff", 500),
        HeadItem("Crown King Gold K", "K", "#ffcc00", 600),
        HeadItem("Devil Red Horn D", "D", "#ff0000", 700)
    )

    val levelCosts = listOf(
        0, 1500, 3000, 5000, 7000, 9000, 11000, 13000,
        16000, 19000, 22000, 26000, 30000, 35000, 42000
    )

    val levelConfigs = listOf(
        LevelConfig(0, 0, 0, 0, "#000000", "#ff88cc", 0, "Lv1 Neon Dot Ring - FREE"),
        LevelConfig(1, 1, 1, 1, "#1a1200", "#ffcc00", 1, "Lv2 Yellow Micro4"),
        LevelConfig(2, 2, 2, 2, "#001a1a", "#ffffff", 2, "Lv3 White Small Star"),
        LevelConfig(3, 3, 3, 3, "#001a2a", "#00ffff", 3, "Lv4 Red Glow"),
        LevelConfig(4, 4, 4, 4, "#0a1a00", "#55ff00", 4, "Lv5 White Orange"),
        LevelConfig(5, 5, 5, 5, "#0f0f0f", "#aaaaaa", 5, "Lv6 Red Big Glow"),
        LevelConfig(6, 6, 6, 6, "#1a0000", "#ff0000", 6, "Lv7 Yellow Ring"),
        LevelConfig(7, 7, 7, 7, "#001a1a", "#ff00ff", 7, "Lv8 White Ball Micro"),
        LevelConfig(8, 8, 8, 8, "#001a33", "#0099ff", 8, "Lv9 Orange Dot"),
        LevelConfig(9, 9, 9, 9, "#330000", "#ff3300", 9, "Lv10 Cyan Diamond Star"),
        LevelConfig(10, 10, 10, 0, "#0a1a0a", "#00ff88", 10, "Lv11 Pink Ring Big"),
        LevelConfig(11, 11, 11, 1, "#331100", "#ff9900", 10, "Lv12 Yellow Brown"),
        LevelConfig(12, 12, 12, 2, "#001133", "#00aaff", 11, "Lv13 White Pink Star"),
        LevelConfig(13, 13, 13, 3, "#332200", "#ffcc00", 11, "Lv14 White Small Dot"),
        LevelConfig(14, 14, 14, 9, "#220000", "#ff1493", 12, "Lv15 Final Glow")
    )

    fun encodeCoins(coins: Int): String {
        val safeCoins = coins.coerceAtLeast(0)
        val raw = (safeCoins * 7 + 123).toString().toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    fun decodeCoins(encoded: String?): Int {
        if (encoded.isNullOrBlank()) return START_COINS
        return try {
            val decodedBytes = Base64.decode(encoded, Base64.DEFAULT)
            val num = String(decodedBytes, Charsets.UTF_8).trim().toIntOrNull() ?: return START_COINS
            val value = (num - 123) / 7
            if (value < 0) START_COINS else value
        } catch (_: Exception) {
            START_COINS
        }
    }

    fun safeFood(index: Int): FoodItem = foodItems[index.coerceIn(0, foodItems.size - 1)]
    fun safeBody(index: Int): BodyItem = bodyItems[index.coerceIn(0, bodyItems.size - 1)]
    fun safeFull(index: Int): FullBodyItem = fullBodies[index.coerceIn(0, fullBodies.size - 1)]
    fun safeHead(index: Int): HeadItem = heads[index.coerceIn(0, heads.size - 1)]
    fun safeLevelIndex(levelNumber: Int): Int = (levelNumber - 1).coerceIn(0, levelConfigs.size - 1)
    fun safeLevel(levelNumber: Int): LevelConfig = levelConfigs[safeLevelIndex(levelNumber)]
}
