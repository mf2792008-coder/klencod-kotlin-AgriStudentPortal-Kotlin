package com.mustafa.agriportal

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class WebViewActivity : Activity() {
    companion object {
        const val EXTRA_TITLE = "page_title"
        const val EXTRA_URL = "page_url"
    }

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(11, 89, 66)
        window.navigationBarColor = Color.rgb(11, 89, 66)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "صفحة الطالب"
        val url = intent.getStringExtra(EXTRA_URL) ?: "https://menofia.edu.eg"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.WHITE)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(11, 89, 66))
        }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { finish() }
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(45), dp(48)))
        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        }
        toolbar.addView(titleView, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(toolbar)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))

        webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                // Keep normal secure web links inside the app. Non-web schemes are not loaded in WebView.
                return scheme != "https" && scheme != "http"
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                val scheme = android.net.Uri.parse(url).scheme?.lowercase()
                return scheme != "https" && scheme != "http"
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                Toast.makeText(this@WebViewActivity, "تعذر التحقق من أمان اتصال الموقع", Toast.LENGTH_LONG).show()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) android.view.View.GONE else android.view.View.VISIBLE
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        webView.loadUrl(url)
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
