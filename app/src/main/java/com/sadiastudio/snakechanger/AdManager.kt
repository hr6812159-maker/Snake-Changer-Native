    @SuppressLint("SetJavaScriptEnabled")
    fun triggerTopBannerAd() {
        if (isDestroyed || activity.isFinishing || hasTriggeredTopAd) return
        hasTriggeredTopAd = true

        val rootGroup: ViewGroup? = activity.findViewById(android.R.id.content) ?: return

        val adWebView = WebView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(76),
                Gravity.TOP
            )
            visibility = View.VISIBLE
            setBackgroundColor(Color.TRANSPARENT)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }
            loadDataWithBaseURL(
                GameData.MONETAG_BASE_URL,
                """
                    <!DOCTYPE html>
                    <html>
                    <head>
                      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                      <meta name="monetag" content="${GameData.MONETAG_META_TAG}">
                      <style>
                        * { box-sizing: border-box; margin: 0; padding: 0; }
                        html, body { width: 100%; height: 100%; background: transparent; overflow: visible; }
                      </style>
                    </head>
                    <body>
                      <script async src="${GameData.MONETAG_SCRIPT_SRC}" data-zone="${GameData.BANNER_ZONE_ID}"></script>
                    </body>
                    </html>
                """.trimIndent(),
                "text/html",
                "UTF-8",
                null
            )
        }

        topAdWebView = adWebView
        rootGroup?.addView(adWebView)
    }
