package com.sadiastudio.snakechanger

import android.util.Base64

data class LevelCfg(
    val name: String,
    val bgHex: String,
    val trailHex: String,
    val speedBonus: Int,
    val full: Int
)

data class FoodItem(
    val name: String,
    val shape: String,
    val colorHex: String,
    val price: Int
)

data class BodyItem(
    val name: String,
    val pattern: String,
    val colorHex: String,
    val price: Int
)

data class FullBodyItem(
    val name: String,
    val pattern: String,
    val dotHex: String,
    val centerHex: String,
    val size: Float,
    val price: Int
)

data class HeadItem(
    val name: String,
    val emoji: String,
    val colorHex: String,
    val price: Int
)

object GameData {
    const val GAME_NAME = "Snake Changer"
    const val BRAND = "SGS"
    const val STUDIO = "Sadia Studio"
    const val DEVELOPER = "HR"
    const val UPTODOWN_URL = "https://snake-changer.en.uptodown.com/android"

    const val MONETAG_BASE_URL = "https://hr6812159-maker.github.io"
    const val MONETAG_SCRIPT_SRC = "https://nap5k.com/tag.min.js"
    const val MONETAG_META_TAG = "2053640a97828312fa739634bf108857"
    const val BANNER_ZONE_ID = "12205061"
    const val SPIN_REWARD_ZONE_ID = "12205081"

    const val START_COINS = 500
    const val FOOD_EARN = 50
    const val SPECIAL_COIN_EARN = 200
    const val RATE_BONUS_COINS = 300
    private const val COIN_SALT = "SGS_SECURE_SALT_2026_"

    val spinRewards = listOf(1500, 2000, 3000)

    val levelCosts = listOf(
        0, 1200, 1800, 2500, 3200,
        4000, 4800, 5600, 6500, 7500,
        8500, 9600, 10800, 12000, 13500
    )

    val levels = listOf(
        LevelCfg("Lv1: Neon Grid", "#000000", "#ff00ff", 0, 0),
        LevelCfg("Lv2: Cyber Alley", "#050a14", "#00ffff", 4, 1),
        LevelCfg("Lv3: Solar Arena", "#120703", "#ff9900", 8, 2),
        LevelCfg("Lv4: Toxic Lab", "#041208", "#00ff88", 12, 3),
        LevelCfg("Lv5: Crimson Core", "#160308", "#ff0066", 16, 4),
        LevelCfg("Lv6: Plasma Orbit", "#070518", "#7b2cbf", 20, 5),
        LevelCfg("Lv7: Glacial Drift", "#03121a", "#4cc9f0", 24, 6),
        LevelCfg("Lv8: Golden Vault", "#141002", "#ffd166", 28, 7),
        LevelCfg("Lv9: Emerald Nexus", "#03140e", "#06d6a0", 32, 8),
        LevelCfg("Lv10: Hyper Pulse", "#12041c", "#f72585", 36, 9),
        LevelCfg("Lv11: Volt Matrix", "#061114", "#00f5d4", 40, 10),
        LevelCfg("Lv12: Amber Blaze", "#170a02", "#fb5607", 44, 11),
        LevelCfg("Lv13: Cobalt Void", "#04091a", "#3a86ff", 48, 12),
        LevelCfg("Lv14: Lime Sector", "#0b1403", "#aacc00", 52, 13),
        LevelCfg("Lv15: SGS Omega", "#0a0414", "#ffbe0b", 56, 14)
    )

    val foodItems = listOf(
        FoodItem("Classic Apple", "apple", "#ff3b30", 0),
        FoodItem("Star Gem", "star", "#ffeb3b", 400),
        FoodItem("Cyan Diamond", "diamond", "#00ffff", 600),
        FoodItem("Ruby Heart", "heart", "#ff0066", 800),
        FoodItem("Neon Triangle", "triangle", "#00ff88", 1000),
        FoodItem("Cyber Square", "square", "#3a86ff", 1200),
        FoodItem("Hex Core", "hexagon", "#8338ec", 1400),
        FoodItem("Crescent Moon", "moon", "#ffd166", 1600),
        FoodItem("Aqua Drop", "drop", "#4cc9f0", 1800),
        FoodItem("Plus Cross", "cross", "#06d6a0", 2000),
        FoodItem("Royal Crown", "crown", "#ffbe0b", 2200),
        FoodItem("Spicy Chilli", "chilli", "#fb5607", 2400),
        FoodItem("Twin Berry", "berry", "#f72585", 2600),
        FoodItem("Solar Lemon", "lemon", "#aacc00", 2800),
        FoodItem("Prism Rainbow", "rainbow", "#ff00ff", 3200)
    )

    val bodyItems = listOf(
        BodyItem("Solid Coral", "solid", "#ff4d6d", 0),
        BodyItem("Shadow Stripe", "striped", "#00f5d4", 450),
        BodyItem("Pearl Dot", "dotted", "#ffbe0b", 650),
        BodyItem("Aura Gradient", "gradient", "#3a86ff", 850),
        BodyItem("Pulse Neon", "neon", "#00ff88", 1100),
        BodyItem("Grid Checker", "checker", "#fb5607", 1300),
        BodyItem("Orbit Ring", "ring", "#8338ec", 1500),
        BodyItem("Sine Wave", "wave", "#4cc9f0", 1700),
        BodyItem("Zigzag Spark", "zigzag", "#ff006e", 1900),
        BodyItem("Dragon Scale", "scales", "#06d6a0", 2100),
        BodyItem("Jungle Camo", "camo", "#aacc00", 2300),
        BodyItem("Magma Fire", "fire", "#ff3300", 2500),
        BodyItem("Frost Ice", "ice", "#00aaff", 2700),
        BodyItem("Starlight Galaxy", "galaxy", "#5500ff", 2900),
        BodyItem("Hyper Rainbow", "rainbow", "#f72585", 3400)
    )

    val fullBodies = listOf(
        FullBodyItem("Micro Dot 4", "dotMicro4", "#00f5d4", "#ffffff", 3.6f, 0),
        FullBodyItem("Cyber Ring", "dotRing", "#ffbe0b", "#00ffff", 4.0f, 500),
        FullBodyItem("Starlight", "smallStar", "#ff006e", "#ffffff", 4.2f, 750),
        FullBodyItem("Diamond Spark", "diamondStar", "#3a86ff", "#ffeb3b", 4.4f, 1000),
        FullBodyItem("Neon Glow", "finalGlow", "#00ff88", "#ffffff", 4.6f, 1250),
        FullBodyItem("Pulse Ring", "ring", "#8338ec", "#00f5d4", 4.1f, 1500),
        FullBodyItem("Pink Nova", "starPink", "#f72585", "#ffffff", 4.3f, 1750),
        FullBodyItem("Mega Halo", "ringBig", "#ffd166", "#ff006e", 4.8f, 2000),
        FullBodyItem("Core Beacon", "glowDot", "#06d6a0", "#ffffff", 4.2f, 2250),
        FullBodyItem("Titan Glow", "bigGlow", "#fb5607", "#ffeb3b", 4.9f, 2500),
        FullBodyItem("Aqua Quad", "dotMicro4", "#4cc9f0", "#ffffff", 4.0f, 2750),
        FullBodyItem("Prism Ring", "dotRing", "#aacc00", "#00ffff", 4.4f, 3000),
        FullBodyItem("Vortex Star", "smallStar", "#ffbe0b", "#ff006e", 4.5f, 3250),
        FullBodyItem("Crystal Shard", "diamondStar", "#00f5d4", "#ffffff", 4.6f, 3500),
        FullBodyItem("SGS Supreme", "finalGlow", "#ff006e", "#ffeb3b", 5.0f, 4000)
    )

    val heads = listOf(
        HeadItem("Classic Bot", "SGS", "#00f5d4", 0),
        HeadItem("Cool Shade", "😎", "#ffbe0b", 500),
        HeadItem("Fierce Viper", "🐍", "#00ff88", 750),
        HeadItem("Fire Blaze", "🔥", "#fb5607", 1000),
        HeadItem("Star King", "⭐", "#ffeb3b", 1250),
        HeadItem("Royal Crown", "👑", "#ffd166", 1500),
        HeadItem("Lightning", "⚡", "#3a86ff", 1750),
        HeadItem("Alien Core", "👾", "#8338ec", 2000),
        HeadItem("Dragon Eye", "🐲", "#06d6a0", 2250),
        HeadItem("Diamond Head", "💎", "#4cc9f0", 2500),
        HeadItem("Heart Pulse", "💖", "#ff006e", 2750),
        HeadItem("Rocket Dash", "🚀", "#f72585", 3000),
        HeadItem("Shadow Ninja", "🥷", "#aacc00", 3250),
        HeadItem("Cyber Skull", "🤖", "#00ffff", 3500),
        HeadItem("Omega Boss", "👑", "#ffbe0b", 4000)
    )

    fun safeLevel(lv: Int): LevelCfg = levels[(lv - 1).coerceIn(0, levels.lastIndex)]
    fun safeFood(idx: Int): FoodItem = foodItems[idx.coerceIn(0, foodItems.lastIndex)]
    fun safeBody(idx: Int): BodyItem = bodyItems[idx.coerceIn(0, bodyItems.lastIndex)]
    fun safeFull(idx: Int): FullBodyItem = fullBodies[idx.coerceIn(0, fullBodies.lastIndex)]
    fun safeHead(idx: Int): HeadItem = heads[idx.coerceIn(0, heads.lastIndex)]

    fun encodeCoins(amount: Int): String {
        val safe = amount.coerceAtLeast(0)
        val raw = "$COIN_SALT$safe"
        return Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun decodeCoins(encoded: String?): Int {
        if (encoded.isNullOrBlank()) return START_COINS
        return try {
            val decoded = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
            val numStr = decoded.removePrefix(COIN_SALT)
            numStr.toInt().coerceAtLeast(0)
        } catch (_: Exception) {
            START_COINS
        }
    }
}
