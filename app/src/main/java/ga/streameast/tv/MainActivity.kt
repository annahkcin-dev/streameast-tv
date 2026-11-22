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
package ga.streameast.tv

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private val appVersionTag = "TV v1.1"

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

        // Support HTML5 Fullscreen video API cleanly on Google TV
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                val decor = window.decorView as FrameLayout
                decor.addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                val decor = window.decorView as FrameLayout
                customView?.let { decor.removeView(it) }
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                webView.visibility = View.VISIBLE
            }
        }

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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isDpadOrBack = keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ||
                keyCode == KeyEvent.KEYCODE_MEDIA_REWIND ||
                keyCode == KeyEvent.KEYCODE_BACK

        if (isDpadOrBack) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val jsKey = when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> "ArrowUp"
                    KeyEvent.KEYCODE_DPAD_DOWN -> "ArrowDown"
                    KeyEvent.KEYCODE_DPAD_LEFT -> "ArrowLeft"
                    KeyEvent.KEYCODE_DPAD_RIGHT -> "ArrowRight"
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> "Enter"
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "MediaPlayPause"
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "MediaFastForward"
                    KeyEvent.KEYCODE_MEDIA_REWIND -> "MediaRewind"
                    KeyEvent.KEYCODE_BACK -> "Back"
                    else -> ""
                }
                if (jsKey.isNotEmpty()) {
                    val script = "window.__streamEastHandleTvKey ? window.__streamEastHandleTvKey('$jsKey') : false;"
                    webView.evaluateJavascript(script) { result ->
                        if (keyCode == KeyEvent.KEYCODE_BACK && result != "true") {
                            if (customView != null) {
                                webView.webChromeClient?.onHideCustomView()
                            } else if (webView.canGoBack()) {
                                webView.goBack()
                            } else {
                                finish()
                            }
                        }
                    }
                    return true
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun injectTvEnhancements(view: WebView?) {
        val js = """
            (function() {
                if (window.__streamEastTvInjected) return;
                window.__streamEastTvInjected = true;

                // 1. Force 1040px desktop viewport so Google TV scales edge-to-edge cleanly
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    document.head.appendChild(meta);
                }
                meta.content = 'width=1040';

                // 2. Inject TV styling: full-bleed theater video mode, hidden controls until requested, high-contrast green/white focus ring
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
                    /* High-visibility YouTube-style focus ring for D-pad navigation */
                    a:focus, button:focus, input:focus, select:focus, [tabindex]:focus, .streameast-tv-focused {
                        outline: 3px solid #ffffff !important;
                        outline-offset: 3px !important;
                        box-shadow: 0 0 0 6px rgba(34, 197, 94, 0.85) !important;
                        border-radius: 8px !important;
                    }
                    /* Fullscreen Theater Lock: expands video wrapper edge-to-edge and hides surrounding page UI */
                    .streameast-tv-theater-wrapper {
                        position: fixed !important;
                        top: 0 !important;
                        left: 0 !important;
                        width: 100vw !important;
                        height: 100vh !important;
                        max-width: 100vw !important;
                        max-height: 100vh !important;
                        z-index: 2147483000 !important;
                        background: #000000 !important;
                        margin: 0 !important;
                        padding: 0 !important;
                    }
                    .streameast-tv-theater-wrapper video {
                        width: 100% !important;
                        height: 100% !important;
                        object-fit: contain !important;
                    }
                    /* Keep player overlay controls completely hidden until user explicitly opens HUD */
                    body.streameast-tv-hud-hidden .streameast-tv-theater-wrapper button,
                    body.streameast-tv-hud-hidden .streameast-tv-theater-wrapper [role="button"],
                    body.streameast-tv-hud-hidden .streameast-tv-theater-wrapper [class*="control"],
                    body.streameast-tv-hud-hidden .streameast-tv-theater-wrapper [class*="overlay"],
                    body.streameast-tv-hud-hidden .streameast-tv-theater-wrapper [class*="header"],
                    body.streameast-tv-hud-hidden .streameast-tv-theater-wrapper [class*="top"],
                    body.streameast-tv-hud-hidden .streameast-tv-theater-wrapper [class*="bottom"],
                    body.streameast-tv-hud-hidden .streameast-tv-theater-wrapper input[type="range"] {
                        opacity: 0 !important;
                        pointer-events: none !important;
                    }
                    /* Hide non-TV clutter buttons inside active video player */
                    .streameast-tv-hide-clutter {
                        display: none !important;
                    }
                    /* YouTube-style HUD toast banner for playback/seek feedback */
                    #streameast-tv-hud-toast {
                        position: fixed;
                        top: 28px;
                        left: 50%;
                        transform: translateX(-50%);
                        padding: 10px 22px;
                        border-radius: 999px;
                        background: rgba(11, 17, 32, 0.90);
                        border: 1px solid rgba(34, 197, 94, 0.55);
                        color: #ffffff;
                        font-family: system-ui, -apple-system, sans-serif;
                        font-size: 16px;
                        font-weight: 600;
                        letter-spacing: 0.3px;
                        z-index: 2147483647;
                        pointer-events: none;
                        opacity: 0;
                    }
                    #streameast-tv-hud-toast.visible {
                        opacity: 1;
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

                // 3. Version badge & HUD feedback pill
                if (!document.getElementById('streameast-tv-version-badge')) {
                    var badge = document.createElement('div');
                    badge.id = 'streameast-tv-version-badge';
                    badge.textContent = '$appVersionTag';
                    document.body.appendChild(badge);
                }
                var toast = document.getElementById('streameast-tv-hud-toast');
                if (!toast) {
                    toast = document.createElement('div');
                    toast.id = 'streameast-tv-hud-toast';
                    document.body.appendChild(toast);
                }

                var toastTimer = null;
                function showHudToast(msg) {
                    toast.textContent = msg;
                    toast.classList.add('visible');
                    clearTimeout(toastTimer);
                    toastTimer = setTimeout(function() {
                        toast.classList.remove('visible');
                    }, 1400);
                }

                // 4. Block native web player keydown handlers from double-firing volume/seek on Arrow keys
                window.addEventListener('keydown', function(e) {
                    if (e.isTrusted && (e.key === 'ArrowUp' || e.key === 'ArrowDown' || e.key === 'ArrowLeft' || e.key === 'ArrowRight')) {
                        e.stopImmediatePropagation();
                        e.preventDefault();
                    }
                }, true);

                // 5. YouTube-Style Fullscreen Theater + D-pad Engine
                var hudOpen = false;
                var userMovedFocusInHud = false;
                var hudHideTimer = null;
                var activeTheaterWrapper = null;
                var exitedTheaterManually = false;

                function getActiveVideo() {
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        if (v.offsetWidth > 150 && v.offsetHeight > 100) {
                            return v;
                        }
                    }
                    return videos.length > 0 ? videos[0] : null;
                }

                function findPlayerWrapper(video) {
                    if (!video) return null;
                    var el = video.parentElement;
                    var best = el;
                    while (el && el !== document.body) {
                        var rect = el.getBoundingClientRect();
                        if (rect.width >= video.offsetWidth && rect.height >= video.offsetHeight && rect.height < window.innerHeight * 0.98) {
                            best = el;
                        }
                        if (el.matches && el.matches('[class*="player"], [id*="player"], .video-js, .plyr, .jwplayer')) {
                            best = el;
                            break;
                        }
                        el = el.parentElement;
                    }
                    return best;
                }

                function enterTheaterFullscreen(video) {
                    if (!video || exitedTheaterManually) return;
                    var wrapper = findPlayerWrapper(video);
                    if (wrapper && !wrapper.classList.contains('streameast-tv-theater-wrapper')) {
                        if (activeTheaterWrapper && activeTheaterWrapper !== wrapper) {
                            activeTheaterWrapper.classList.remove('streameast-tv-theater-wrapper');
                        }
                        wrapper.classList.add('streameast-tv-theater-wrapper');
                        activeTheaterWrapper = wrapper;
                        closePlayerHud(video);
                    }
                }

                function exitTheaterFullscreen() {
                    if (activeTheaterWrapper) {
                        activeTheaterWrapper.classList.remove('streameast-tv-theater-wrapper');
                        activeTheaterWrapper = null;
                    }
                    document.body.classList.remove('streameast-tv-hud-hidden');
                    hudOpen = false;
                    userMovedFocusInHud = false;
                }

                function wakePlayerOverlay(video) {
                    var target = activeTheaterWrapper || (video && video.parentElement) || video;
                    if (!target) return;
                    var rect = target.getBoundingClientRect();
                    var cx = rect.left + rect.width / 2;
                    var cy = rect.top + rect.height / 2;
                    target.dispatchEvent(new MouseEvent('mousemove', { bubbles: true, clientX: cx, clientY: cy }));
                    target.dispatchEvent(new PointerEvent('pointermove', { bubbles: true, clientX: cx, clientY: cy }));
                }

                function isBackButton(el) {
                    if (!el) return false;
                    var label = ((el.getAttribute('aria-label') || '') + ' ' + (el.getAttribute('title') || '') + ' ' + (el.textContent || '')).toLowerCase().trim();
                    return label === 'back' || label.indexOf('go back') !== -1 || label.indexOf('return') !== -1;
                }

                function openPlayerHud(video) {
                    hudOpen = true;
                    userMovedFocusInHud = false;
                    document.body.classList.remove('streameast-tv-hud-hidden');
                    wakePlayerOverlay(video);
                    resetHudAutoHide();
                    if (document.activeElement && document.activeElement.blur) {
                        document.activeElement.blur();
                    }
                }

                function closePlayerHud(video) {
                    hudOpen = false;
                    userMovedFocusInHud = false;
                    document.body.classList.add('streameast-tv-hud-hidden');
                    clearTimeout(hudHideTimer);
                    if (document.activeElement && document.activeElement.blur) {
                        document.activeElement.blur();
                    }
                    if (video) {
                        var target = activeTheaterWrapper || video.parentElement || video;
                        target.dispatchEvent(new MouseEvent('mouseleave', { bubbles: true }));
                    }
                }

                function resetHudAutoHide() {
                    clearTimeout(hudHideTimer);
                    hudHideTimer = setTimeout(function() {
                        var v = getActiveVideo();
                        if (v && !v.paused) {
                            closePlayerHud(v);
                        }
                    }, 4500);
                }

                function formatTime(sec) {
                    if (!isFinite(sec) || sec < 0) sec = 0;
                    var m = Math.floor(sec / 60);
                    var s = Math.floor(sec % 60);
                    return m + ':' + (s < 10 ? '0' : '') + s;
                }

                function getVisibleFocusables(insidePlayerOnly) {
                    var root = (insidePlayerOnly && activeTheaterWrapper) ? activeTheaterWrapper : document;
                    var selector = 'a[href], button, input, select, [tabindex]:not([tabindex="-1"]), [role="button"], [role="switch"]';
                    var nodes = Array.prototype.slice.call(root.querySelectorAll(selector));
                    return nodes.filter(function(el) {
                        if (el.disabled || el.classList.contains('streameast-tv-hide-clutter')) return false;
                        if (insidePlayerOnly && isBackButton(el)) return false;
                        var style = window.getComputedStyle(el);
                        if (style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity) === 0) return false;
                        var rect = el.getBoundingClientRect();
                        return rect.width > 4 && rect.height > 4;
                    });
                }

                function moveSpatialFocus(direction, insidePlayerOnly) {
                    var candidates = getVisibleFocusables(insidePlayerOnly);
                    if (candidates.length === 0) return;

                    var current = document.activeElement;
                    if (!current || candidates.indexOf(current) === -1) {
                        candidates[0].focus();
                        candidates[0].scrollIntoView({ block: 'nearest', inline: 'nearest' });
                        return;
                    }

                    var curRect = current.getBoundingClientRect();
                    var curCx = curRect.left + curRect.width / 2;
                    var curCy = curRect.top + curRect.height / 2;

                    var best = null;
                    var bestScore = Infinity;

                    for (var i = 0; i < candidates.length; i++) {
                        var el = candidates[i];
                        if (el === current) continue;
                        var r = el.getBoundingClientRect();
                        var cx = r.left + r.width / 2;
                        var cy = r.top + r.height / 2;
                        var dx = cx - curCx;
                        var dy = cy - curCy;

                        var inDirection = false;
                        var primaryDist = 0;
                        var crossDist = 0;

                        if (direction === 'ArrowRight' && dx > 8) {
                            inDirection = true; primaryDist = dx; crossDist = Math.abs(dy);
                        } else if (direction === 'ArrowLeft' && dx < -8) {
                            inDirection = true; primaryDist = -dx; crossDist = Math.abs(dy);
                        } else if (direction === 'ArrowDown' && dy > 8) {
                            inDirection = true; primaryDist = dy; crossDist = Math.abs(dx);
                        } else if (direction === 'ArrowUp' && dy < -8) {
                            inDirection = true; primaryDist = -dy; crossDist = Math.abs(dx);
                        }

                        if (inDirection) {
                            var score = primaryDist + (crossDist * 2.5);
                            if (score < bestScore) {
                                bestScore = score;
                                best = el;
                            }
                        }
                    }

                    if (best) {
                        best.focus();
                        best.scrollIntoView({ block: 'nearest', inline: 'nearest' });
                    }
                }

                // Main key router called from Android dispatchKeyEvent
                window.__streamEastHandleTvKey = function(key) {
                    var video = getActiveVideo();

                    // A. VIDEO PLAYBACK MODE
                    if (video) {
                        if (key === 'MediaPlayPause') {
                            if (video.paused) { video.play(); showHudToast('▶ Playing'); resetHudAutoHide(); }
                            else { video.pause(); showHudToast('❚❚ Paused'); openPlayerHud(video); }
                            return true;
                        }
                        if (key === 'MediaFastForward') {
                            video.currentTime = Math.min(video.duration || video.currentTime + 10, video.currentTime + 10);
                            showHudToast('⏩ +10s (' + formatTime(video.currentTime) + ')');
                            return true;
                        }
                        if (key === 'MediaRewind') {
                            video.currentTime = Math.max(0, video.currentTime - 10);
                            showHudToast('⏪ -10s (' + formatTime(video.currentTime) + ')');
                            return true;
                        }

                        // State 1: HUD is HIDDEN (clean edge-to-edge fullscreen watching)
                        if (!hudOpen) {
                            if (key === 'ArrowLeft') {
                                video.currentTime = Math.max(0, video.currentTime - 10);
                                showHudToast('⏪ -10s (' + formatTime(video.currentTime) + ')');
                                return true;
                            }
                            if (key === 'ArrowRight') {
                                video.currentTime = Math.min(video.duration || video.currentTime + 10, video.currentTime + 10);
                                showHudToast('⏩ +10s (' + formatTime(video.currentTime) + ')');
                                return true;
                            }
                            if (key === 'ArrowUp' || key === 'ArrowDown') {
                                openPlayerHud(video);
                                setTimeout(function() {
                                    moveSpatialFocus('ArrowRight', true);
                                    userMovedFocusInHud = true;
                                }, 50);
                                return true;
                            }
                            if (key === 'Enter') {
                                if (video.paused) {
                                    video.play();
                                    showHudToast('▶ Playing');
                                    openPlayerHud(video);
                                } else {
                                    video.pause();
                                    showHudToast('❚❚ Paused');
                                    openPlayerHud(video);
                                }
                                return true;
                            }
                            if (key === 'Back') {
                                if (activeTheaterWrapper) {
                                    exitedTheaterManually = true;
                                    exitTheaterFullscreen();
                                    return true;
                                }
                                return false;
                            }
                        }

                        // State 2: HUD is OPEN
                        if (hudOpen) {
                            resetHudAutoHide();
                            wakePlayerOverlay(video);

                            if (key === 'Back') {
                                closePlayerHud(video);
                                return true;
                            }
                            if (key === 'ArrowUp' || key === 'ArrowDown' || key === 'ArrowLeft' || key === 'ArrowRight') {
                                userMovedFocusInHud = true;
                                moveSpatialFocus(key, true);
                                return true;
                            }
                            if (key === 'Enter') {
                                var active = document.activeElement;
                                if (userMovedFocusInHud && active && active !== document.body && active !== video && !isBackButton(active)) {
                                    active.click();
                                } else {
                                    if (video.paused) {
                                        video.play();
                                        showHudToast('▶ Playing');
                                        resetHudAutoHide();
                                    } else {
                                        video.pause();
                                        showHudToast('❚❚ Paused');
                                    }
                                }
                                return true;
                            }
                        }
                    }

                    // B. BROWSE / MENU MODE
                    if (key === 'Back') {
                        return false;
                    }
                    if (key === 'ArrowUp' || key === 'ArrowDown' || key === 'ArrowLeft' || key === 'ArrowRight') {
                        moveSpatialFocus(key, false);
                        return true;
                    }
                    if (key === 'Enter') {
                        var el = document.activeElement;
                        if (el && el !== document.body) {
                            el.click();
                        }
                        return true;
                    }
                    return false;
                };

                // 6. Auto-attach fullscreen theater mode & clutter cleanup whenever a video loads/plays
                function attachVideoHooks() {
                    var video = getActiveVideo();
                    if (!video) {
                        if (activeTheaterWrapper) {
                            exitTheaterFullscreen();
                        }
                        exitedTheaterManually = false;
                        return;
                    }

                    if (!video.__streamEastHooked) {
                        video.__streamEastHooked = true;
                        exitedTheaterManually = false;
                        enterTheaterFullscreen(video);

                        video.addEventListener('play', function() {
                            if (!exitedTheaterManually) {
                                enterTheaterFullscreen(video);
                            }
                        });
                        video.addEventListener('loadeddata', function() {
                            if (!exitedTheaterManually) {
                                enterTheaterFullscreen(video);
                            }
                        });
                    } else if (!exitedTheaterManually && !activeTheaterWrapper) {
                        enterTheaterFullscreen(video);
                    }

                    var container = activeTheaterWrapper || video.closest('[class*="player"], [id*="player"], .video-js, .plyr, .jwplayer') || video.parentElement;
                    if (!container) return;

                    var hiddenLabels = ['picture in picture', 'pip', 'cast', 'chromecast', 'download', 'fullscreen', 'volume', 'mute'];
                    var buttons = container.querySelectorAll('button, [role="button"]');
                    for (var i = 0; i < buttons.length; i++) {
                        var btn = buttons[i];
                        var label = ((btn.getAttribute('aria-label') || '') + ' ' + (btn.getAttribute('title') || '') + ' ' + (btn.textContent || '')).toLowerCase().trim();
                        for (var j = 0; j < hiddenLabels.length; j++) {
                            if (label === hiddenLabels[j] || label.indexOf(hiddenLabels[j]) !== -1) {
                                btn.classList.add('streameast-tv-hide-clutter');
                                break;
                            }
                        }
                    }
                }

                attachVideoHooks();

                var observer = new MutationObserver(function() {
                    attachVideoHooks();
                });
                observer.observe(document.body, { childList: true, subtree: true });
            })();
        """.trimIndent()

        view?.evaluateJavascript(js, null)
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
