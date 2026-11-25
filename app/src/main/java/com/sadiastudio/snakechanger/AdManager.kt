package com.sadiastudio.snakechanger

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView

class AdManager(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var spinAdWebView: WebView? = null

    fun attachBanner(bannerContainer: WebView?) {
        val webView = bannerContainer ?: return
        configureWebView(webView)
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
              <meta name="monetag" content="${GameData.MONETAG_META_TOKEN}">
              <style>
                html, body {
                  margin: 0; padding: 0; width: 100%; height: 100%;
                  background: #111111; color: #00ffff;
                  display: flex; align-items: center; justify-content: center;
                  font-family: sans-serif; font-size: 9px; overflow: hidden;
                }
              </style>
            </head>
            <body>
              <script>(function(s){s.dataset.zone='${GameData.BANNER_ZONE_ID}',s.src='${GameData.BANNER_SCRIPT_URL}'})([document.documentElement, document.body].filter(Boolean).pop().appendChild(document.createElement('script')))</script>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL("https://hr6812159-maker.github.io/", html, "text/html", "UTF-8", null)
    }

    fun showSpinRewardedAd(onRewardGranted: () -> Unit, onStatusUpdate: ((String) -> Unit)? = null) {
        onStatusUpdate?.invoke("Loading Spin Ad (${GameData.REWARDED_ZONE_ID})...")

        val wv = spinAdWebView ?: WebView(context).also {
            configureWebView(it)
            spinAdWebView = it
        }

        val spinAdHtml = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <meta name="monetag" content="${GameData.MONETAG_META_TOKEN}">
            </head>
            <body style="margin:0;background:#000;">
              <script>(function(s){s.dataset.zone='${GameData.REWARDED_ZONE_ID}',s.src='${GameData.REWARDED_SCRIPT_URL}'})([document.documentElement, document.body].filter(Boolean).pop().appendChild(document.createElement('script')))</script>
            </body>
            </html>
        """.trimIndent()
        wv.loadDataWithBaseURL("https://hr6812159-maker.github.io/", spinAdHtml, "text/html", "UTF-8", null)

        // Spin wheel ad flow: grant reward once spin ad step completes
        handler.postDelayed({
            onRewardGranted()
        }, 1200)
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        spinAdWebView?.destroy()
        spinAdWebView = null
    }

    private fun configureWebView(webView: WebView) {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
    }
}
