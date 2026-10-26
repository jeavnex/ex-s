package app.exs.client

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast

class MainActivity : Activity(), SafeWebViewClient.Listener {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var updateManager: UpdateManager
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private val isTelevision by lazy {
        val mode = (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType
        mode == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature("android.software.leanback")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = if (isTelevision) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        buildInterface()
        configureWebView()
        updateManager = UpdateManager(this).also { it.register() }
        if (savedInstanceState == null) webView.loadUrl(HOME_URL) else webView.restoreState(savedInstanceState)
        updateManager.check(showUpToDate = false)
    }

    private fun buildInterface() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(9, 12, 18)) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9, 12, 18))
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.rgb(16, 23, 36))
        }
        toolbar.addView(actionButton("Главная") { webView.loadUrl(HOME_URL) })
        toolbar.addView(actionButton("Назад") { if (webView.canGoBack()) webView.goBack() })
        toolbar.addView(actionButton("Вперёд") { if (webView.canGoForward()) webView.goForward() })
        toolbar.addView(actionButton("Обновить") { webView.reload() })
        toolbar.addView(actionButton("Войти") { openLogin() })
        toolbar.addView(actionButton("Версия") { updateManager.check(showUpToDate = true) })

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        webView = WebView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.rgb(9, 12, 18))
        }
        content.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(if (isTelevision) 64 else 52)))
        content.addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
        content.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    private fun actionButton(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = if (isTelevision) 18f else 13f
        isAllCaps = false
        isFocusable = true
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(29, 45, 68))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            marginEnd = dp(4)
        }
        setOnFocusChangeListener { view, focused ->
            view.setBackgroundColor(if (focused) Color.rgb(40, 126, 214) else Color.rgb(29, 45, 68))
            view.animate().scaleX(if (focused) 1.04f else 1f).scaleY(if (focused) 1.04f else 1f).setDuration(100).start()
        }
    }

    private fun configureWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = false
            userAgentString = "$userAgentString EX-S/${BuildConfig.VERSION_NAME}"
            builtInZoomControls = !isTelevision
            displayZoomControls = false
            loadWithOverviewMode = !isTelevision
            useWideViewPort = !isTelevision
        }
        webView.webViewClient = SafeWebViewClient(this)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, progress: Int) {
                progressBar.progress = progress
                progressBar.visibility = if (progress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message,
            ): Boolean = false

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                content.visibility = View.GONE
                root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }

            override fun onHideCustomView() = hideCustomView()
        }
        webView.setDownloadListener { _, _, _, _, _ ->
            Toast.makeText(this, "Загрузки с сайта в EX-S отключены", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openLogin() {
        val script = """
            (() => {
              const link = Array.from(document.querySelectorAll('a')).find(
                (item) => (item.textContent || '').toLowerCase().includes('авторизац')
              );
              if (link) link.click();
            })();
        """.trimIndent()
        if (DomainPolicy.isAllowedTopLevel(webView.url.orEmpty())) {
            webView.evaluateJavascript(script, null)
        } else {
            webView.loadUrl(HOME_URL)
            webView.postDelayed({ webView.evaluateJavascript(script, null) }, 1_500)
        }
    }

    private fun hideCustomView() {
        val view = customView ?: return
        root.removeView(view)
        content.visibility = View.VISIBLE
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    override fun onLoadingChanged(loading: Boolean) {
        if (loading) progressBar.visibility = View.VISIBLE
    }

    override fun onBlockedNavigation(url: String) {
        val host = runCatching { android.net.Uri.parse(url).host }.getOrNull() ?: "неизвестный сайт"
        Toast.makeText(this, "EX-S заблокировал переход на $host", Toast.LENGTH_SHORT).show()
    }

    override fun onMainPageError(message: String) {
        Toast.makeText(this, "Сайт недоступен: $message", Toast.LENGTH_LONG).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            when {
                customView != null -> hideCustomView()
                webView.canGoBack() -> webView.goBack()
                else -> return super.onKeyDown(keyCode, event)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        updateManager.unregister()
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val HOME_URL = "https://ex-fs.net/"
    }
}
