package app.exs.client

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExsActivity : ComponentActivity() {
    private var state by mutableStateOf(ExsState())
    private lateinit var web: WebView
    private lateinit var updateManager: UpdateManager
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenTapPending = false
    private var adapterReady = false
    private var pendingCommand: Pair<String, JSONObject>? = null
    private val recentLogs = mutableMapOf<String, Long>()
    private val isTelevision by lazy {
        val mode = (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType
        mode == Configuration.UI_MODE_TYPE_TELEVISION || packageManager.hasSystemFeature("android.software.leanback")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = if (isTelevision) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        applyImmersive()
        createWebView()
        updateManager = UpdateManager(this).also { it.register() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })
        val activityCallback = window.callback
        window.callback = object : android.view.Window.Callback by activityCallback {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                handlePlayerKey(event) || activityCallback.dispatchKeyEvent(event)
        }
        setContent {
            val savedScreens = rememberSaveableStateHolder()
            Box(Modifier.fillMaxSize()) {
                AndroidView(factory = { web }, modifier = Modifier.fillMaxSize(), update = { view ->
                    val surface = state.screen == AppScreen.LOGIN || (state.screen == AppScreen.PLAYER && state.playerReady)
                    val becomingVisible = surface && view.visibility != View.VISIBLE
                    view.visibility = if (surface) View.VISIBLE else View.INVISIBLE
                    view.isFocusable = surface
                    view.isFocusableInTouchMode = surface
                    if (becomingVisible) view.requestFocus()
                    if (!surface) view.clearFocus()
                })
                if (state.screen == AppScreen.CATALOG || state.screen == AppScreen.DETAIL || state.screen == AppScreen.DIAGNOSTICS) {
                    savedScreens.SaveableStateProvider("${state.screen}:${state.section}") {
                    ExsApp(state, ::openSection, ::openDetail, ::search, ::play, ::handleBack, ::openLogin, ::refresh,
                        { checkUpdates(true) }, ::openDiagnostics, ::clearLogs, ::shareLogs)
                    }
                }
                if (state.screen == AppScreen.PLAYER) {
                    PlayerChrome(state, ::handleBack, ::toggleOrientation)
                }
            }
        }
        addLog("Запуск EX-S ${BuildConfig.VERSION_NAME}; Android ${Build.VERSION.RELEASE}; SDK ${Build.VERSION.SDK_INT}; TV=$isTelevision")
        web.loadUrl(SITE)
        checkUpdates(false)
    }

    private fun checkUpdates(showUpToDate: Boolean) {
        state = state.copy(updateStatus = UpdateStatus.CHECKING)
        updateManager.check(showUpToDate) { release ->
            state = when {
                release == null -> state.copy(updateStatus = UpdateStatus.UNAVAILABLE)
                UpdateManager.compareVersions(release.version, BuildConfig.VERSION_NAME) > 0 ->
                    state.copy(updateStatus = UpdateStatus.OUTDATED, latestVersion = release.version)
                else -> state.copy(updateStatus = UpdateStatus.CURRENT, latestVersion = release.version)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            overScrollMode = View.OVER_SCROLL_NEVER
            isFocusable = false
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = false
            settings.useWideViewPort = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.userAgentString += " EX-S/${BuildConfig.VERSION_NAME}" + if (isTelevision) " Android TV" else ""
        }
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(web, true) }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            state = state.copy(loading = false, error = "Обновите Android System WebView — он нужен для безопасной работы EX-S.")
            return
        }
        WebViewCompat.addWebMessageListener(web, "EXSBridge", setOf(SITE_ORIGIN)) { _, message, origin, mainFrame, _ ->
            if (!mainFrame || origin.scheme != "https" || origin.host != SITE_HOST) return@addWebMessageListener
            message.data?.takeIf { it.length < 1_500_000 }?.let { runCatching { onSiteEvent(JSONObject(it)) } }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.isForMainFrame && !DomainPolicy.isAllowedTopLevel(request.url.toString())

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (isProblemVideoPoster(request)) {
                    runOnUiThread { addLog("Заменён несовместимый постер видео: ${safeUrl(request.url.toString())}") }
                    return WebResourceResponse("image/gif", "binary", 200, "OK", mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "no-store"), java.io.ByteArrayInputStream(TRANSPARENT_GIF))
                }
                if (!DomainPolicy.isBlockedResource(request.url.toString())) return null
                runOnUiThread { addLog("Заблокирован ресурс ${safeUrl(request.url.toString())}") }
                return WebResourceResponse("text/plain", "UTF-8", 204, "No Content", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                adapterReady = false
                addLog("Страница начала загрузку: ${safeUrl(url)}")
                if (state.screen == AppScreen.PLAYER) state = state.copy(playerReady = false, loading = true)
            }
            override fun onPageFinished(view: WebView, url: String?) {
                if (url != null && DomainPolicy.isAllowedTopLevel(url)) {
                    addLog("Страница загружена: ${safeUrl(url)}")
                    view.evaluateJavascript(assets.open("exfs-adapter.js").bufferedReader().use { it.readText() }, null)
                    CookieManager.getInstance().flush()
                }
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel(); state = state.copy(loading = false, error = "Ошибка защищённого соединения с EX-FS.")
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                addLog("WebView ошибка ${error.errorCode}: ${safeUrl(request.url.toString())} — ${error.description}")
                if (request.isForMainFrame) state = state.copy(loading = false, error = "EX-FS сейчас не отвечает. Проверьте сеть или доступ из вашей страны.")
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean = false
            override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                val value = message.message()
                val noise = value.contains("font-size:0") || value.contains("deviceorientation") ||
                    value.contains("Permissions policy violation") || value.contains("Unrecognized feature") ||
                    value.contains("No available adapters") || value.contains("console.groupEnd") || value.contains("OTS parsing")
                if (!noise) addLog("JS ${message.messageLevel()}: $value")
                return true
            }
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) { callback.onCustomViewHidden(); return }
                customView = view
                customViewCallback = callback
                (window.decorView as ViewGroup).addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                if (!isTelevision) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                addLog("Оригинальный плеер: нативный fullscreen открыт; поворот датчиком разрешён")
                applyImmersive()
            }
            override fun onHideCustomView() = hideCustomView()
        }
    }

    private fun command(name: String, payload: JSONObject = JSONObject()) {
        addLog("Команда: $name")
        if (!adapterReady) { pendingCommand = name to payload; addLog("Команда ожидает загрузки адаптера"); return }
        web.evaluateJavascript("window.EXSApp&&window.EXSApp.command(${JSONObject.quote(name)},${payload});", null)
    }

    private fun openSection(section: CatalogSection) {
        addLog("Раздел: ${section.label}")
        state = state.copy(section = section, detail = null, query = "", cards = emptyList(), loading = true, error = null)
        command("catalog", json("path" to section.path, "query" to ""))
    }
    private fun search(query: String) {
        state = state.copy(query = query, loading = true, error = null)
        command("catalog", json("path" to state.section.path, "query" to query.trim().take(120)))
    }
    private fun openDetail(card: MediaCard) {
        addLog("Карточка: ${card.title}")
        state = state.copy(screen = AppScreen.DETAIL, detail = MediaDetail(card), loading = true, error = null)
        command("detail", json("url" to card.url))
    }
    private fun play(index: Int) {
        val detail = state.detail ?: return
        addLog("Запуск плеера #$index: ${detail.card.title}")
        fullscreenTapPending = true
        if (!isTelevision) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        state = state.copy(screen = AppScreen.PLAYER, playerReady = false, loading = true, error = null)
        command("play", json("url" to detail.card.url, "player" to index))
    }
    private fun openLogin() { state = state.copy(screen = AppScreen.LOGIN, loading = false, error = null); command("login") }
    private fun openDiagnostics() { addLog("Открыта диагностика"); state = state.copy(screen = AppScreen.DIAGNOSTICS, loading = false, error = null) }
    private fun clearLogs() { state = state.copy(logs = emptyList()); addLog("Журнал очищен") }
    private fun shareLogs() {
        val report = diagnosticReport()
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "EX-S ${BuildConfig.VERSION_NAME} — диагностика")
            putExtra(Intent.EXTRA_TEXT, report)
        }, "Отправить журнал EX-S"))
    }
    private fun refresh() {
        state = state.copy(error = null, loading = true)
        if (!adapterReady) web.reload() else when (state.screen) {
            AppScreen.CATALOG -> command("catalog", json("path" to state.section.path, "query" to state.query))
            AppScreen.DETAIL -> state.detail?.let { command("detail", json("url" to it.card.url)) }
            AppScreen.PLAYER -> state.detail?.let { command("play", json("url" to it.card.url, "player" to 0)) }
            AppScreen.LOGIN -> command("login")
            AppScreen.DIAGNOSTICS -> Unit
        }
    }
    private fun handleBack() {
        when (state.screen) {
            AppScreen.PLAYER -> {
                fullscreenTapPending = false
                if (customView != null) hideCustomView()
                command("closePlayer")
                if (!isTelevision) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                state = state.copy(screen = AppScreen.DETAIL, playerReady = false, loading = false, error = null)
            }
            AppScreen.LOGIN -> { command("closeLogin"); state = state.copy(screen = AppScreen.CATALOG, loading = false, error = null); openSection(state.section) }
            AppScreen.DETAIL -> state = state.copy(screen = AppScreen.CATALOG, loading = false, error = null)
            AppScreen.DIAGNOSTICS -> state = state.copy(screen = AppScreen.CATALOG, loading = false, error = null)
            AppScreen.CATALOG -> moveTaskToBack(true)
        }
    }

    // Fullscreen WebView owns a separate surface: Activity.onKeyDown is not
    // guaranteed to see its keys. Handle media actions before dispatch to it.
    private fun handlePlayerKey(event: KeyEvent): Boolean {
        if (state.screen == AppScreen.PLAYER) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) handleBack()
                return true
            }
            val action = when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY -> "play"
                KeyEvent.KEYCODE_MEDIA_PAUSE -> "pause"
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "toggle"
                KeyEvent.KEYCODE_MEDIA_REWIND -> "left"
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "right"
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (isTelevision) "toggle" else null
                KeyEvent.KEYCODE_DPAD_LEFT -> if (isTelevision) "left" else null
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (isTelevision) "right" else null
                KeyEvent.KEYCODE_DPAD_UP -> if (isTelevision) "controls" else null
                KeyEvent.KEYCODE_DPAD_DOWN -> if (isTelevision) "video" else null
                else -> null
            }
            if (action != null) {
                if (event.action == KeyEvent.ACTION_DOWN && (event.repeatCount == 0 || action == "left" || action == "right")) {
                    addLog("Пульт: $action; repeat=${event.repeatCount}")
                    web.evaluateJavascript("window.EXSApp&&window.EXSApp.playerKey(${JSONObject.quote(action)});", null)
                }
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_MENU) {
                if (event.action == KeyEvent.ACTION_UP) { handleBack(); openDiagnostics() }
                return true
            }
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (state.screen == AppScreen.PLAYER) when (keyCode) {
            KeyEvent.KEYCODE_BACK -> { handleBack(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> return web.dispatchKeyEvent(event)
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (state.screen == AppScreen.PLAYER && keyCode in setOf(
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            )) return web.dispatchKeyEvent(event)
        return super.onKeyUp(keyCode, event)
    }

    private fun onSiteEvent(event: JSONObject) {
        val type = event.optString("type")
        if (type != "catalog") addLog("Событие сайта: $type")
        when (type) {
            "ready" -> {
                adapterReady = true
                val pending = pendingCommand; pendingCommand = null
                if (pending != null) command(pending.first, pending.second) else if (state.cards.isEmpty() && state.screen == AppScreen.CATALOG) openSection(state.section)
            }
            "catalog" -> state = state.copy(cards = event.optJSONArray("items").objects().map {
                MediaCard(it.optString("url"), it.optString("title"), it.optString("poster"), it.optString("meta"), it.optString("badge"))
            }, loading = false, error = null)
            "detail" -> {
                val old = state.detail?.card ?: return
                val players = event.optJSONArray("players").objects().mapIndexed { i, p -> PlayerChoice(p.optInt("index", i), p.optString("label", "Плеер ${i + 1}")) }
                val card = old.copy(title = event.optString("title", old.title), poster = event.optString("poster", old.poster), meta = event.optString("meta", old.meta))
                state = state.copy(detail = MediaDetail(card, event.optString("originalTitle"), event.optString("description"), event.optString("facts"), event.optString("rating"), players), loading = false, error = null)
            }
            "playerReady" -> { addLog("Плеер готов: ${safeUrl(event.optString("provider"))}"); state = state.copy(playerReady = true, loading = false, error = null) }
            "fullscreenRequest" -> {
                // Only tap the adapter's own fullscreen button, once per explicit Play action.
                // WebView requires a trusted gesture before it will show its fullscreen surface.
                if (state.screen == AppScreen.PLAYER && fullscreenTapPending && customView == null) {
                    fullscreenTapPending = false
                    web.postDelayed({
                        if (state.screen == AppScreen.PLAYER && customView == null) {
                            val x = 24f * resources.displayMetrics.density
                            val y = 24f * resources.displayMetrics.density
                            val time = SystemClock.uptimeMillis()
                            val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0)
                            val up = MotionEvent.obtain(time, time + 60, MotionEvent.ACTION_UP, x, y, 0)
                            web.dispatchTouchEvent(down)
                            web.dispatchTouchEvent(up)
                            down.recycle(); up.recycle()
                            addLog("Оригинальный плеер: запрошен нативный fullscreen")
                        }
                    }, 200)
                }
            }
            "diagnostic" -> addLog("Плеер: ${event.optString("message")}")
            "auth" -> state = state.copy(loggedIn = event.optBoolean("loggedIn"), accountName = event.optString("displayName"))
            "loginComplete" -> { state = state.copy(screen = AppScreen.CATALOG, loggedIn = true, loading = true); openSection(state.section) }
            "error" -> state = state.copy(loading = false, error = event.optString("message", "Не удалось загрузить данные."))
        }
    }

    override fun onPause() { if (::web.isInitialized) { web.onPause(); CookieManager.getInstance().flush() }; super.onPause() }
    override fun onResume() { super.onResume(); if (::web.isInitialized) web.onResume() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) applyImmersive() }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyImmersive()
        if (state.screen == AppScreen.PLAYER) {
            web.post { web.requestLayout(); command("resizePlayer") }
            addLog("Поворот плеера: ${newConfig.orientation}; ${newConfig.screenWidthDp}x${newConfig.screenHeightDp}dp")
        }
    }
    override fun onDestroy() { if (customView != null) hideCustomView(); updateManager.unregister(); web.stopLoading(); web.destroy(); super.onDestroy() }

    private fun playerAction(action: String, keyCode: Int) {
        web.evaluateJavascript("window.EXSApp&&window.EXSApp.playerKey(${JSONObject.quote(action)});", null)
        web.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        web.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun toggleOrientation() {
        if (isTelevision) return
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    private fun hideCustomView() {
        val view = customView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        addLog("Оригинальный плеер: нативный fullscreen закрыт")
        applyImmersive()
    }

    private fun applyImmersive() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
    private fun addLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val safe = message.replace(Regex("https?://[^\\s\\\"'<>]+")) { safeUrl(it.value) }.take(700)
        val now = System.currentTimeMillis()
        if (now - (recentLogs[safe] ?: 0L) < 2500L) return
        recentLogs[safe] = now
        state = state.copy(logs = (state.logs + "[$time] $safe").takeLast(500))
    }
    private fun isProblemVideoPoster(request: WebResourceRequest): Boolean {
        val host = request.url.host.orEmpty().lowercase()
        val path = request.url.path.orEmpty().lowercase()
        return host.endsWith("uacdn.online") && (path.contains("@h") ||
            (path.contains("/videos/") && request.requestHeaders["Accept"]?.contains("image") == true))
    }
    private fun safeUrl(value: String?): String {
        if (value.isNullOrBlank()) return "—"
        return runCatching { Uri.parse(value).let { "${it.scheme}://${it.host}${it.path.orEmpty()}" } }.getOrDefault("неизвестный адрес")
    }
    private fun diagnosticReport() = buildString {
        appendLine("EX-S ${BuildConfig.VERSION_NAME}")
        appendLine("Android ${Build.VERSION.RELEASE}; SDK ${Build.VERSION.SDK_INT}; TV=$isTelevision")
        appendLine("Экран: ${state.screen}; раздел: ${state.section.label}")
        appendLine("--- ЖУРНАЛ ---")
        append(state.logs.joinToString("\n"))
    }
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    private fun json(vararg fields: Pair<String, Any?>) = JSONObject().apply { fields.forEach { put(it.first, it.second) } }

    companion object {
        private const val SITE = "https://ex-fs.net/"
        private const val SITE_ORIGIN = "https://ex-fs.net"
        private const val SITE_HOST = "ex-fs.net"
        private val TRANSPARENT_GIF = android.util.Base64.decode("R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs=", android.util.Base64.DEFAULT)
    }
}
