package ga.streameast.tv

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val appVersionTag = "TV v1.0"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Deep dark slate background so there is zero white flash on launch
        window.decorView.setBackgroundColor(Color.parseColor("#0B1120"))

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1120"))
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }
        setContentView(webView)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            // Fit 1040px desktop layout edge-to-edge left-to-right on Google TV
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        }

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectTvEnhancements(view)
            }
        }

        webView.loadUrl("https://v2.streameast.ga/nfl-streams/")
    }

    private fun injectTvEnhancements(view: WebView?) {
        val js = """
            (function() {
                if (window.__streamEastTvInjected) return;
                window.__streamEastTvInjected = true;

                // Force 1040px desktop viewport so Google TV scales edge-to-edge cleanly
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    document.head.appendChild(meta);
                }
                meta.content = 'width=1040';

                // Inject TV styling: edge-to-edge fit, D-pad focus rings, reduced motion, version badge
                var style = document.createElement('style');
                style.id = 'streameast-tv-style';
                style.textContent = `
                    html, body {
                        background-color: #0B1120 !important;
                        width: 100% !important;
                        max-width: 100% !important;
                        overflow-x: hidden !important;
                        margin: 0 !important;
                    }
                    *, *::before, *::after {
                        animation-duration: 0.01ms !important;
                        animation-iteration-count: 1 !important;
                        transition-duration: 0.01ms !important;
                        scroll-behavior: auto !important;
                    }
                    a:focus, button:focus, input:focus, [tabindex]:focus {
                        outline: 3px solid #22c55e !important;
                        outline-offset: 3px !important;
                        box-shadow: 0 0 16px rgba(34, 197, 94, 0.85) !important;
                        border-radius: 8px !important;
                    }
                    #streameast-tv-version-badge {
                        position: fixed;
                        bottom: 10px;
                        right: 14px;
                        padding: 3px 8px;
                        border-radius: 6px;
                        background: rgba(11, 17, 32, 0.8);
                        border: 1px solid rgba(34, 197, 94, 0.4);
                        color: rgba(134, 239, 172, 0.8);
                        font-family: system-ui, -apple-system, sans-serif;
                        font-size: 11px;
                        font-weight: 600;
                        letter-spacing: 0.5px;
                        z-index: 99998;
                        pointer-events: none;
                    }
                `;
                document.head.appendChild(style);

                if (!document.getElementById('streameast-tv-version-badge')) {
                    var badge = document.createElement('div');
                    badge.id = 'streameast-tv-version-badge';
                    badge.textContent = '$appVersionTag';
                    document.body.appendChild(badge);
                }
            })();
        """.trimIndent()

        view?.evaluateJavascript(js, null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }
}
