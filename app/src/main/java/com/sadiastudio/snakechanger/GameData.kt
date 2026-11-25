package com.sadiastudio.snakechanger

import android.util.Base64

data class FoodItem(
    val name: String,
    val color: String,
    val shape: String,
    val price: Int
)

data class BodyItem(
    val name: String,
    val color: String,
    val pattern: String,
    val price: Int
)

data class FullBody(
    val name: String,
    val dot: String,
    val color: String,
    val center: String,
    val pattern: String,
    val size: Float,
    val price: Int
)

data class HeadItem(
    val name: String,
    val emoji: String,
    val color: String,
    val price: Int
)

data class LevelConfig(
    val food: Int,
    val body: Int,
    val full: Int,
    val head: Int,
    val bg: String,
    val trail: String,
    val speedBonus: Int,
    val name: String
)

object GameData {
    const val GAME_NAME = "Snake Changer"
    const val BRAND = "SGS"
    const val STUDIO = "Sadia Studio"
    const val DEVELOPER = "Habibur Rahman"
    const val VERSION_NAME = "1.1.0"

    const val FOOD_EARN = 50
    const val SPECIAL_EARN = 150
    const val START_COINS = 500
    const val RATE_BONUS_COINS = 100

    val foodItems = listOf(
        FoodItem("Red Apple Round", "#FF1144", "apple", 100),
        FoodItem("Yellow Star", "#FFEB3B", "star", 150),
        FoodItem("Purple Diamond", "#AA00FF", "diamond", 200),
        FoodItem("Cyan Heart", "#00E5FF", "heart", 250),
        FoodItem("Green Triangle", "#00FF88", "triangle", 300),
        FoodItem("Pink Square", "#FF88CC", "square", 350),
        FoodItem("Orange Hexagon", "#FF9900", "hexagon", 400),
        FoodItem("White Moon", "#FFFFFF", "moon", 450),
        FoodItem("Blue Drop", "#0099FF", "drop", 500),
        FoodItem("Lime Cross", "#AAFF00", "cross", 550),
        FoodItem("Gold Crown Food", "#FFCC00", "crown", 600),
        FoodItem("Red Chilli", "#FF3300", "chilli", 650),
        FoodItem("Blue Berry", "#00AAFF", "berry", 700),
        FoodItem("Yellow Lemon", "#FFFF00", "lemon", 750),
        FoodItem("Rainbow Mix", "#FF00FF", "rainbow", 800)
    )

    val bodyItems = listOf(
        BodyItem("Solid Red", "#FF3300", "solid", 100),
        BodyItem("Striped Yellow Black", "#FFEB3B", "striped", 150),
        BodyItem("Dotted White", "#FFFFFF", "dotted", 200),
        BodyItem("Gradient Cyan Blue", "#00E5FF", "gradient", 250),
        BodyItem("Neon Glow Pink", "#FF88CC", "neon", 300),
        BodyItem("Checker Green", "#00FF88", "checker", 350),
        BodyItem("Ring Hollow Yellow", "#FFCC00", "ring", 400),
        BodyItem("Wave Line Orange", "#FF9900", "wave", 450),
        BodyItem("Zigzag Purple", "#AA00FF", "zigzag", 500),
        BodyItem("Scales Pattern", "#00FFCC", "scales", 550),
        BodyItem("Camo Pattern", "#55AA55", "camo", 600),
        BodyItem("Fire Gradient", "#FF4400", "fire", 650),
        BodyItem("Ice Blue Glow", "#00AAFF", "ice", 700),
        BodyItem("Galaxy Dot", "#5500FF", "galaxy", 750),
        BodyItem("Rainbow Strip Final", "#FF00FF", "rainbow", 800)
    )

    val fullBodies = listOf(
        FullBody("Neon Dot Ring Pink", "#FF88CC", "#FF88CC", "#FFFFFF", "dotRing", 3.8f, 150),
        FullBody("Yellow 4 Micro Dot", "#FFFFAA", "#FFCC00", "#FFFFAA", "dotMicro4", 3.5f, 200),
        FullBody("White Small Star 5 Kona", "#FFFFFF", "#FFFFFF", "#FFFFAA", "smallStar", 3.2f, 250),
        FullBody("Red Glow Dot Shadow", "#FF1144", "#FF1144", "#FFFF00", "glowDot", 4.0f, 300),
        FullBody("White Orange Center Dot", "#FFFFFF", "#FFFFFF", "#FF9900", "dotCenter", 3.6f, 350),
        FullBody("Red Big Glow Yellow Center", "#FF0000", "#FF0000", "#FFEB3B", "bigGlow", 4.2f, 400),
        FullBody("Yellow Ring Hollow", "#FFCC00", "#FFCC00", "#FF6600", "ring", 3.8f, 450),
        FullBody("White Ball Micro Dot Small", "#FFFFFF", "#FFFFFF", "#FFFFAA", "ballMicro", 3.4f, 500),
        FullBody("Orange Dot Solid", "#FF6600", "#FF6600", "#FF0000", "dot", 4.0f, 550),
        FullBody("Cyan Diamond Star Unique", "#00FFFF", "#00FFFF", "#FFFFFF", "diamondStar", 3.3f, 600),
        FullBody("Pink Ring Big Thick", "#FF55AA", "#FF55AA", "#FFFF00", "ringBig", 4.1f, 650),
        FullBody("Yellow Brown Center Dot", "#FFEB3B", "#FFEB3B", "#553300", "dotCenter", 4.3f, 700),
        FullBody("White Pink 3 Dot Star", "#FFFFFF", "#FFFFFF", "#FF88CC", "starPink", 3.5f, 750),
        FullBody("White Small Dot Minimal", "#FFFFEE", "#FFFFEE", "#FFFF00", "dotSmall", 3.2f, 800),
        FullBody("Final Pink Yellow Double Glow Border", "#FF1493", "#FF1493", "#FFCC00", "finalGlow", 4.5f, 900)
    )

    val heads = listOf(
        HeadItem("Normal Round Yellow", "@", "#FFEB3B", 100),
        HeadItem("Smile Cyan", "C", "#00FFFF", 150),
        HeadItem("Sleep Closed White", "-", "#FFFFAA", 200),
        HeadItem("Eyes Big OO White", "OO", "#FFFFFF", 250),
        HeadItem("Angry Red ><", "><", "#FF3300", 300),
        HeadItem("Star Yellow *", "*", "#FFEB3B", 350),
        HeadItem("Heart Red H", "H", "#FF1144", 400),
        HeadItem("SGS Logo Cyan SGS", "SGS", "#00FFFF", 500),
        HeadItem("Crown King Gold K", "K", "#FFCC00", 600),
        HeadItem("Devil Red Horn D", "D", "#FF0000", 700)
    )

    val levelCosts = listOf(
        0, 1500, 3000, 5000, 7000, 9000, 11000, 13000,
        16000, 19000, 22000, 26000, 30000, 35000, 42000
    )

    val levelConfigs = listOf(
        LevelConfig(0, 0, 0, 0, "#000000", "#FF88CC", 0, "Lv1 Neon Dot Ring - FREE"),
        LevelConfig(1, 1, 1, 1, "#1A1200", "#FFCC00", 1, "Lv2 Yellow Micro4"),
        LevelConfig(2, 2, 2, 2, "#001A1A", "#FFFFFF", 2, "Lv3 White Small Star"),
        LevelConfig(3, 3, 3, 3, "#001A2A", "#00FFFF", 3, "Lv4 Red Glow"),
        LevelConfig(4, 4, 4, 4, "#0A1A00", "#55FF00", 4, "Lv5 White Orange"),
        LevelConfig(5, 5, 5, 5, "#0F0F0F", "#AAAAAA", 5, "Lv6 Red Big Glow"),
        LevelConfig(6, 6, 6, 6, "#1A0000", "#FF0000", 6, "Lv7 Yellow Ring"),
        LevelConfig(7, 7, 7, 7, "#001A1A", "#FF00FF", 7, "Lv8 White Ball Micro"),
        LevelConfig(8, 8, 8, 8, "#001A33", "#0099FF", 8, "Lv9 Orange Dot"),
        LevelConfig(9, 9, 9, 9, "#330000", "#FF3300", 9, "Lv10 Cyan Diamond Star"),
        LevelConfig(10, 10, 10, 0, "#0A1A0A", "#00FF88", 10, "Lv11 Pink Ring Big"),
        LevelConfig(11, 11, 11, 1, "#331100", "#FF9900", 10, "Lv12 Yellow Brown"),
        LevelConfig(12, 12, 12, 2, "#001133", "#00AAFF", 11, "Lv13 White Pink Star"),
        LevelConfig(13, 13, 13, 3, "#332200", "#FFCC00", 11, "Lv14 White Small Dot"),
        LevelConfig(14, 14, 14, 9, "#220000", "#FF1493", 12, "Lv15 Final Glow")
    )

    val spinRewards = listOf(1500, 2000, 3000, 1500, 2000, 3000)

    fun encodeCoins(coins: Int): String {
        val safeCoins = coins.coerceAtLeast(0)
        return Base64.encodeToString(
            (safeCoins * 7 + 123).toString().toByteArray(),
            Base64.NO_WRAP
        )
    }

    fun decodeCoins(encoded: String?): Int {
        if (encoded.isNullOrBlank()) return START_COINS
        return try {
            val decoded = String(Base64.decode(encoded, Base64.NO_WRAP))
            val value = (decoded.toInt() - 123) / 7
            value.coerceAtLeast(0)
        } catch (_: Exception) {
            START_COINS
        }
    }

    fun safeFood(index: Int): FoodItem =
        foodItems.getOrElse(index.coerceIn(0, foodItems.size - 1)) { foodItems[0] }

    fun safeBody(index: Int): BodyItem =
        bodyItems.getOrElse(index.coerceIn(0, bodyItems.size - 1)) { bodyItems[0] }

    fun safeFull(index: Int): FullBody =
        fullBodies.getOrElse(index.coerceIn(0, fullBodies.size - 1)) { fullBodies[0] }

    fun safeHead(index: Int): HeadItem =
        heads.getOrElse(index.coerceIn(0, heads.size - 1)) { heads[0] }

    fun safeLevel(index: Int): LevelConfig =
        levelConfigs.getOrElse(index.coerceIn(0, levelConfigs.size - 1)) { levelConfigs[0] }
}
