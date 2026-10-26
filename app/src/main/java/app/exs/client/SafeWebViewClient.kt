package app.exs.client

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

class SafeWebViewClient(
    private val listener: Listener,
) : WebViewClient() {

    interface Listener {
        fun onLoadingChanged(loading: Boolean)
        fun onBlockedNavigation(url: String)
        fun onMainPageError(message: String)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        val url = request.url.toString()
        if (DomainPolicy.isAllowedTopLevel(url)) return false
        listener.onBlockedNavigation(url)
        return true
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        if (!DomainPolicy.isBlockedResource(request.url.toString())) return null
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            204,
            "No Content",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        listener.onLoadingChanged(true)
    }

    override fun onPageFinished(view: WebView, url: String) {
        listener.onLoadingChanged(false)
        if (!DomainPolicy.isAllowedTopLevel(url)) return
        view.evaluateJavascript(SANITIZE_SCRIPT, null)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            listener.onLoadingChanged(false)
            listener.onMainPageError(error.description?.toString().orEmpty())
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        listener.onLoadingChanged(false)
        listener.onMainPageError("Ошибка сертификата. Соединение остановлено для безопасности.")
    }

    companion object {
        private val SANITIZE_SCRIPT = """
            (() => {
              if (window.__exsSafeNavigationInstalled) return;
              window.__exsSafeNavigationInstalled = true;
              const hostAllowed = (value) => {
                try {
                  const u = new URL(value, location.href);
                  return u.hostname === 'ex-fs.net' || u.hostname.endsWith('.ex-fs.net');
                } catch (_) { return false; }
              };
              document.addEventListener('click', (event) => {
                const anchor = event.target && event.target.closest ? event.target.closest('a[href]') : null;
                if (!anchor) return;
                const href = anchor.href;
                const label = (anchor.textContent || '').trim().toLowerCase();
                if (label.includes('авторизац')) return;
                if (!hostAllowed(href)) {
                  event.preventDefault();
                  event.stopImmediatePropagation();
                  return;
                }
                event.preventDefault();
                event.stopImmediatePropagation();
                location.assign(href);
              }, true);
              const selectors = [
                '[class*="popup"]', '[class*="popunder"]', '[id*="popup"]',
                '[class*="advert"]', '[id*="advert"]', '[class*="banner"]'
              ];
              for (const selector of selectors) {
                document.querySelectorAll(selector).forEach((node) => {
                  if (!node.closest('#player, .player, [class*="video"]')) node.remove();
                });
              }
            })();
        """.trimIndent()
    }
}
