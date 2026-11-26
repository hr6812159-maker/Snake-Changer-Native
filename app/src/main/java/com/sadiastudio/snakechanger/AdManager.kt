package com.sadiastudio.snakechanger

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class AdManager(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var bannerWebView: WebView? = null
    private var rewardWebView: WebView? = null

    fun attachBanner(container: ViewGroup) {
        container.removeAllViews()
        val wv = createConfiguredWebView()
        bannerWebView = wv

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        container.addView(wv, lp)

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <meta name="${GameData.MONETAG_META_TAG}" content="${GameData.MONETAG_META_TAG}">
              <style>
                html,body{margin:0;padding:0;width:100%;height:100%;background:#111;overflow:hidden;display:flex;align-items:center;justify-content:center;}
              </style>
            </head>
            <body>
              <script>(function(s){s.dataset.zone='${GameData.BANNER_ZONE_ID}',s.src='${GameData.MONETAG_SCRIPT_SRC}'})([document.documentElement, document.body].filter(Boolean).pop().appendChild(document.createElement('script')))</script>
            </body>
            </html>
        """.trimIndent()

        wv.loadDataWithBaseURL(GameData.MONETAG_BASE_URL, html, "text/html", "UTF-8", null)
    }

    fun loadRewardedSpinAd(container: ViewGroup?) {
        container ?: return
        container.removeAllViews()
        val wv = createConfiguredWebView()
        rewardWebView = wv
        container.addView(
            wv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <meta name="${GameData.MONETAG_META_TAG}" content="${GameData.MONETAG_META_TAG}">
              <style>html,body{margin:0;padding:0;width:100%;height:100%;background:#161622;color:#ffeb3b;font-family:sans-serif;font-size:10px;display:flex;align-items:center;justify-content:center;text-align:center;}</style>
            </head>
            <body>
              <div>Snake Changer Rewarded Ad<br>Zone ${GameData.SPIN_REWARD_ZONE_ID} Active</div>
              <script>(function(s){s.dataset.zone='${GameData.SPIN_REWARD_ZONE_ID}',s.src='${GameData.SPIN_VIGNETTE_SRC}'})([document.documentElement, document.body].filter(Boolean).pop().appendChild(document.createElement('script')))</script>
            </body>
            </html>
        """.trimIndent()
        wv.loadDataWithBaseURL(GameData.MONETAG_BASE_URL, html, "text/html", "UTF-8", null)
    }

    fun syncFirebaseUser(
        uid: String,
        coins: Int,
        curLv: Int,
        high: Int,
        onRemoteLoaded: ((remoteCoins: Int?, remoteLv: Int?, remoteHigh: Int?) -> Unit)? = null
    ) {
        executor.execute {
            try {
                val docUrl = "https://firestore.googleapis.com/v1/projects/${GameData.FIREBASE_PROJECT_ID}/databases/(default)/documents/sgs_users/$uid"
                val patchUrl = "$docUrl?updateMask.fieldPaths=coins&updateMask.fieldPaths=curLv&updateMask.fieldPaths=high"
                val bodyJson = JSONObject().apply {
                    put("fields", JSONObject().apply {
                        put("coins", JSONObject().put("integerValue", coins.coerceAtLeast(0).toString()))
                        put("curLv", JSONObject().put("stringValue", curLv.coerceIn(1, 15).toString()))
                        put("high", JSONObject().put("integerValue", high.coerceAtLeast(0).toString()))
                    })
                }.toString()

                val patchConn = (URL(patchUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PATCH"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                patchConn.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
                patchConn.responseCode
                patchConn.disconnect()

                val getConn = (URL(docUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                if (getConn.responseCode == 200) {
                    val raw = getConn.inputStream.bufferedReader().use { it.readText() }
                    val fields = JSONObject(raw).optJSONObject("fields")
                    val rCoins = fields?.optJSONObject("coins")?.optString("integerValue")?.toIntOrNull()
                    val rLv = fields?.optJSONObject("curLv")?.optString("stringValue")?.toIntOrNull()
                    val rHigh = fields?.optJSONObject("high")?.optString("integerValue")?.toIntOrNull()
                    mainHandler.post { onRemoteLoaded?.invoke(rCoins, rLv, rHigh) }
                }
                getConn.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun createConfiguredWebView(): WebView {
        return WebView(context).apply {
            setBackgroundColor(Color.parseColor("#111111"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webViewClient = WebViewClient()
        }
    }

    fun destroy() {
        bannerWebView?.destroy()
        rewardWebView?.destroy()
        bannerWebView = null
        rewardWebView = null
        executor.shutdownNow()
    }
}
