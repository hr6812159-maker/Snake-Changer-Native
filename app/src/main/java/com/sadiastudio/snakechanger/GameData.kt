package com.sadiastudio.snakechanger

import android.util.Base64

data class LevelCfg(
    val name: String,
    val coinName: String,
    val ruleNote: String,
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

    // Power Coin Economy (1 Power Coin per 10 foods, unified wallet balance)
    const val START_COINS = 5
    const val FOOD_PER_COIN = 10
    const val POWER_COIN_EARN = 1
    const val RATE_BONUS_COINS = 2
    private const val COIN_SALT = "SGS_POWER_COIN_2026_"

    val spinCoinRewards = listOf(1, 2, 3)

    val levelCosts = listOf(
        0, 3, 5, 8, 12,
        16, 22, 28, 35, 45,
        55, 68, 82, 100, 125
    )

    val levels = listOf(
        LevelCfg("Lv1: Neon Grid", "Mint Coin", "10 Food = 1 Mint Coin", "#000000", "#ff00ff", 0, 0),
        LevelCfg("Lv2: Cyber Alley", "Cyber Coin", "Steady pace + Cyber Coin", "#050a14", "#00ffff", 4, 1),
        LevelCfg("Lv3: Solar Arena", "Solar Coin", "Warm speed + Solar Coin", "#120703", "#ff9900", 8, 2),
        LevelCfg("Lv4: Toxic Lab", "Toxic Coin", "Crisp reflex + Toxic Coin", "#041208", "#00ff88", 12, 3),
        LevelCfg("Lv5: Crimson Core", "Ruby Coin", "Sharp turns + Ruby Coin", "#160308", "#ff0066", 16, 4),
        LevelCfg("Lv6: Plasma Orbit", "Plasma Coin", "Orbit flow + Plasma Coin", "#070518", "#7b2cbf", 20, 5),
        LevelCfg("Lv7: Glacial Drift", "Frost Coin", "Cool glide + Frost Coin", "#03121a", "#4cc9f0", 24, 6),
        LevelCfg("Lv8: Golden Vault", "Gold Coin", "Vault pace + Gold Coin", "#141002", "#ffd166", 28, 7),
        LevelCfg("Lv9: Emerald Nexus", "Jade Coin", "Nexus rhythm + Jade Coin", "#03140e", "#06d6a0", 32, 8),
        LevelCfg("Lv10: Hyper Pulse", "Pulse Coin", "Fast pulse + Pulse Coin", "#12041c", "#f72585", 36, 9),
        LevelCfg("Lv11: Volt Matrix", "Volt Coin", "Volt tempo + Volt Coin", "#061114", "#00f5d4", 40, 10),
        LevelCfg("Lv12: Amber Blaze", "Amber Coin", "Blaze speed + Amber Coin", "#170a02", "#fb5607", 44, 11),
        LevelCfg("Lv13: Cobalt Void", "Cobalt Coin", "Void drift + Cobalt Coin", "#04091a", "#3a86ff", 48, 12),
        LevelCfg("Lv14: Lime Sector", "Lime Coin", "Sector rush + Lime Coin", "#0b1403", "#aacc00", 52, 13),
        LevelCfg("Lv15: SGS Omega", "Omega Coin", "Master trial + Omega Coin", "#0a0414", "#ffbe0b", 56, 14)
    )

    val foodItems = listOf(
        FoodItem("Classic Apple", "apple", "#ff3b30", 0),
        FoodItem("Star Gem", "star", "#ffeb3b", 2),
        FoodItem("Cyan Diamond", "diamond", "#00ffff", 3),
        FoodItem("Ruby Heart", "heart", "#ff0066", 4),
        FoodItem("Neon Triangle", "triangle", "#00ff88", 5),
        FoodItem("Cyber Square", "square", "#3a86ff", 7),
        FoodItem("Hex Core", "hexagon", "#8338ec", 9),
        FoodItem("Crescent Moon", "moon", "#ffd166", 11),
        FoodItem("Aqua Drop", "drop", "#4cc9f0", 13),
        FoodItem("Plus Cross", "cross", "#06d6a0", 15),
        FoodItem("Royal Crown", "crown", "#ffbe0b", 18),
        FoodItem("Spicy Chilli", "chilli", "#fb5607", 21),
        FoodItem("Twin Berry", "berry", "#f72585", 24),
        FoodItem("Solar Lemon", "lemon", "#aacc00", 28),
        FoodItem("Prism Rainbow", "rainbow", "#ff00ff", 32)
    )

    val bodyItems = listOf(
        BodyItem("Solid Coral", "solid", "#ff4d6d", 0),
        BodyItem("Shadow Stripe", "striped", "#00f5d4", 2),
        BodyItem("Pearl Dot", "dotted", "#ffbe0b", 3),
        BodyItem("Aura Gradient", "gradient", "#3a86ff", 4),
        BodyItem("Pulse Neon", "neon", "#00ff88", 5),
        BodyItem("Grid Checker", "checker", "#fb5607", 7),
        BodyItem("Orbit Ring", "ring", "#8338ec", 9),
        BodyItem("Sine Wave", "wave", "#4cc9f0", 11),
        BodyItem("Zigzag Spark", "zigzag", "#ff006e", 13),
        BodyItem("Dragon Scale", "scales", "#06d6a0", 15),
        BodyItem("Jungle Camo", "camo", "#aacc00", 18),
        BodyItem("Magma Fire", "fire", "#ff3300", 21),
        BodyItem("Frost Ice", "ice", "#00aaff", 24),
        BodyItem("Starlight Galaxy", "galaxy", "#5500ff", 28),
        BodyItem("Hyper Rainbow", "rainbow", "#f72585", 32)
    )

    val fullBodies = listOf(
        FullBodyItem("Micro Dot 4", "dotMicro4", "#00f5d4", "#ffffff", 3.6f, 0),
        FullBodyItem("Cyber Ring", "dotRing", "#ffbe0b", "#00ffff", 4.0f, 3),
        FullBodyItem("Starlight", "smallStar", "#ff006e", "#ffffff", 4.2f, 4),
        FullBodyItem("Diamond Spark", "diamondStar", "#3a86ff", "#ffeb3b", 4.4f, 6),
        FullBodyItem("Neon Glow", "finalGlow", "#00ff88", "#ffffff", 4.6f, 8),
        FullBodyItem("Pulse Ring", "ring", "#8338ec", "#00f5d4", 4.1f, 10),
        FullBodyItem("Pink Nova", "starPink", "#f72585", "#ffffff", 4.3f, 12),
        FullBodyItem("Mega Halo", "ringBig", "#ffd166", "#ff006e", 4.8f, 15),
        FullBodyItem("Core Beacon", "glowDot", "#06d6a0", "#ffffff", 4.2f, 18),
        FullBodyItem("Titan Glow", "bigGlow", "#fb5607", "#ffeb3b", 4.9f, 21),
        FullBodyItem("Aqua Quad", "dotMicro4", "#4cc9f0", "#ffffff", 4.0f, 24),
        FullBodyItem("Prism Ring", "dotRing", "#aacc00", "#00ffff", 4.4f, 28),
        FullBodyItem("Vortex Star", "smallStar", "#ffbe0b", "#ff006e", 4.5f, 32),
        FullBodyItem("Crystal Shard", "diamondStar", "#00f5d4", "#ffffff", 4.6f, 36),
        FullBodyItem("SGS Supreme", "finalGlow", "#ff006e", "#ffeb3b", 5.0f, 40)
    )

    val heads = listOf(
        HeadItem("Classic Bot", "SGS", "#00f5d4", 0),
        HeadItem("Cool Shade", "😎", "#ffbe0b", 3),
        HeadItem("Fierce Viper", "🐍", "#00ff88", 4),
        HeadItem("Fire Blaze", "🔥", "#fb5607", 6),
        HeadItem("Star King", "⭐", "#ffeb3b", 8),
        HeadItem("Royal Crown", "👑", "#ffd166", 10),
        HeadItem("Lightning", "⚡", "#3a86ff", 12),
        HeadItem("Alien Core", "👾", "#8338ec", 15),
        HeadItem("Dragon Eye", "🐲", "#06d6a0", 18),
        HeadItem("Diamond Head", "💎", "#4cc9f0", 21),
        HeadItem("Heart Pulse", "💖", "#ff006e", 24),
        HeadItem("Rocket Dash", "🚀", "#f72585", 28),
        HeadItem("Shadow Ninja", "🥷", "#aacc00", 32),
        HeadItem("Cyber Skull", "🤖", "#00ffff", 36),
        HeadItem("Omega Boss", "👑", "#ffbe0b", 40)
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
