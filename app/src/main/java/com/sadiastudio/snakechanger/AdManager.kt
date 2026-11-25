package com.sadiastudio.snakechanger

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog

object AdManager {
    private const val BANNER_ZONE_ID = "12205061"
    private const val INTERSTITIAL_ZONE_ID = "12205081"
    private var gameOverCount = 0

    fun initialize(activity: Activity) {
        // Initialize Monetag or mediation SDK here when native AAR is added
    }

    fun getBannerStatusText(): String {
        return "Ad Zone Ready ($BANNER_ZONE_ID)"
    }

    fun showRewardedInterstitial(
        activity: Activity,
        title: String,
        onRewarded: () -> Unit
    ) {
        // Replace simulated countdown with live SDK callback once SDK AAR is attached
        val handler = Handler(Looper.getMainLooper())
        var secondsLeft = 3
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage("Please wait $secondsLeft seconds to complete ad...")
            .setCancelable(false)
            .create()

        dialog.show()

        val tickRunnable = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed) {
                    if (dialog.isShowing) dialog.dismiss()
                    return
                }
                secondsLeft--
                if (secondsLeft > 0) {
                    dialog.setMessage("Please wait $secondsLeft seconds to complete ad...")
                    handler.postDelayed(this, 1000L)
                } else {
                    if (dialog.isShowing) dialog.dismiss()
                    onRewarded()
                }
            }
        }
        handler.postDelayed(tickRunnable, 1000L)
    }

    fun onGameOver(activity: Activity, onComplete: () -> Unit) {
        gameOverCount++
        // Show interstitial every 3 game-over events without blocking gameplay
        if (gameOverCount % 3 == 0) {
            showRewardedInterstitial(
                activity = activity,
                title = "Interstitial Ad ($INTERSTITIAL_ZONE_ID)",
                onRewarded = onComplete
            )
        } else {
            onComplete()
        }
    }
}
