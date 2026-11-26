package com.sadiastudio.snakechanger

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class AdManager(private val activity: Activity) {

    private val handler = Handler(Looper.getMainLooper())
    private var topAdWebView: WebView? = null
    private var rewardModal: FrameLayout? = null
    private var rewardTimerRunnable: Runnable? = null
    private var isDestroyed = false
    private var hasTriggeredTopAd = false

    @SuppressLint("SetJavaScriptEnabled")
    fun triggerTopBannerAd() {
        if (isDestroyed || activity.isFinishing || hasTriggeredTopAd) return
        hasTriggeredTopAd = true

        val rootGroup: ViewGroup? = activity.findViewById(android.R.id.content) ?: return

        val adHolder = WebView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(110),
                Gravity.TOP
            )
            setBackgroundColor(Color.TRANSPARENT)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
              <meta name="monetag" content="${GameData.MONETAG_META_TAG}">
              <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                html, body {
                  width: 100%;
                  height: 100%;
                  background: transparent;
                  overflow: visible;
                }
              </style>
            </head>
            <body>
              <script async src="${GameData.MONETAG_SCRIPT_SRC}" data-zone="${GameData.BANNER_ZONE_ID}"></script>
            </body>
            </html>
        """.trimIndent()

        topAdWebView = adHolder
        rootGroup?.addView(adHolder)
        adHolder.loadDataWithBaseURL(
            GameData.MONETAG_BASE_URL,
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    // Rewarded Ad ONLY for Spin Wheel
    fun showSpinRewardedAd(onRewardGranted: () -> Unit) {
        if (isDestroyed || activity.isFinishing) {
            onRewardGranted()
            return
        }

        dismissRewardModal()

        val rootGroup: ViewGroup? = activity.findViewById(android.R.id.content)
        if (rootGroup == null) {
            onRewardGranted()
            return
        }

        val overlay = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.argb(230, 0, 0, 0))
            isClickable = true
            isFocusable = true
            visibility = View.VISIBLE
        }

        val box = LinearLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                dp(310),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.parseColor("#11111e"))
        }

        val titleTv = TextView(activity).apply {
            text = "SPIN WHEEL REWARD AD"
            setTextColor(Color.CYAN)
            textSize = 13f
            setPadding(0, 0, 0, dp(6))
        }
        box.addView(titleTv)

        val statusTv = TextView(activity).apply {
            text = "Loading reward... 3s"
            setTextColor(Color.parseColor("#00ff88"))
            textSize = 11f
            setPadding(0, 0, 0, dp(10))
        }
        box.addView(statusTv)

        val adWeb = WebView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120)).apply {
                bottomMargin = dp(12)
            }
            @SuppressLint("SetJavaScriptEnabled")
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setBackgroundColor(Color.parseColor("#080810"))
            loadDataWithBaseURL(
                GameData.MONETAG_BASE_URL,
                """
                    <!DOCTYPE html>
                    <html>
                    <head>
                      <meta name="viewport" content="width=device-width, initial-scale=1.0">
                      <meta name="monetag" content="${GameData.MONETAG_META_TAG}">
                      <style>
                        html, body { margin:0; padding:0; width:100%; height:100%; background:#080810; color:#00ff88; display:flex; align-items:center; justify-content:center; font-family:sans-serif; }
                      </style>
                    </head>
                    <body>
                      <div id="reward-slot">SGS Spin Reward Ready</div>
                      <script async src="${GameData.MONETAG_SCRIPT_SRC}" data-zone="${GameData.SPIN_REWARD_ZONE_ID}"></script>
                    </body>
                    </html>
                """.trimIndent(),
                "text/html",
                "UTF-8",
                null
            )
        }
        box.addView(adWeb)

        val claimBtn = Button(activity).apply {
            text = "WAIT (3)"
            isEnabled = false
            setTextColor(Color.BLACK)
            textSize = 11f
            setPadding(dp(18), dp(8), dp(18), dp(8))
            setBackgroundColor(Color.GRAY)
            setOnClickListener {
                dismissRewardModal()
                onRewardGranted()
            }
        }
        box.addView(claimBtn)
        overlay.addView(box)
        rootGroup?.addView(overlay)
        rewardModal = overlay

        var secondsLeft = 3
        val countdown = object : Runnable {
            override fun run() {
                if (isDestroyed || rewardModal == null) return
                secondsLeft--
                if (secondsLeft > 0) {
                    statusTv.text = "Loading reward... ${secondsLeft}s"
                    claimBtn.text = "WAIT ($secondsLeft)"
                    rewardTimerRunnable = this
                    handler.postDelayed(this, 1000L)
                } else {
                    statusTv.text = "Reward unlocked! Tap to claim spin."
                    claimBtn.isEnabled = true
                    claimBtn.text = "CLAIM SPIN REWARD"
                    claimBtn.setBackgroundColor(Color.parseColor("#00ff88"))
                }
            }
        }
        rewardTimerRunnable = countdown
        handler.postDelayed(countdown, 1000L)
    }

    private fun dismissRewardModal() {
        rewardTimerRunnable?.let { handler.removeCallbacks(it) }
        rewardTimerRunnable = null
        rewardModal?.let { modal ->
            (modal.parent as? ViewGroup)?.removeView(modal)
        }
        rewardModal = null
    }

    fun destroy() {
        isDestroyed = true
        dismissRewardModal()
        topAdWebView?.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        topAdWebView = null
    }

    private fun dp(value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }
}
