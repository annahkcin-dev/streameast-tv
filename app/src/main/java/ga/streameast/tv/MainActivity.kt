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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        }

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
                enableImmersiveMode()
            }

            override fun onHideCustomView() {
                val decor = window.decorView as FrameLayout
                customView?.let { decor.removeView(it) }
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                webView.visibility = View.VISIBLE
                enableImmersiveMode()
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                val host = request.url?.host?.lowercase() ?: ""
                if (url.startsWith("intent:") || url.startsWith("market:")) return true
                if (host.isNotEmpty() &&
                    !host.contains("streameast") &&
                    !host.contains("premium") &&
                    !host.contains("embed") &&
                    !host.contains("stream") &&
                    !host.contains("player") &&
                    !host.contains("cdn") &&
                    !host.contains("cloudflare")
                ) {
                    if (request.isForMainFrame) return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectTvEnhancements()
            }
        }

        enableImmersiveMode()
        webView.loadUrl("https://streameastapp.info/")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveMode()
    }

    @Suppress("DEPRECATION")
    private fun enableImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    sendTvKey("UP")
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    sendTvKey("DOWN")
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    sendTvKey("LEFT")
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    sendTvKey("RIGHT")
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    sendTvKey("CENTER")
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    sendTvKey("PLAY_PAUSE")
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    handleBackKey()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun sendTvKey(key: String) {
        webView.evaluateJavascript("window.__tvHandleKey && window.__tvHandleKey('$key');", null)
    }

    private fun handleBackKey() {
        webView.evaluateJavascript(
            "(function(){ return !!(window.__tvHandleBack && window.__tvHandleBack()); })()"
        ) { result ->
            if (result != "true") {
                if (customView != null) {
                    webView.webChromeClient?.onHideCustomView()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        }
    }

    private fun injectTvEnhancements() {
        val js = """
            (function() {
                if (window.__tvInjected) {
                    if (window.__tvRefresh) window.__tvRefresh();
                    return;
                }
                window.__tvInjected = true;

                const LEAGUES = ['NFL', 'NBA', 'MLB', 'NHL', 'MMA', 'BOXING', 'NCAA', 'WWE'];

                function isStreamPage() {
                    const p = location.pathname.toLowerCase();
                    if (p.includes('/custom-stream/') || p.includes('/stream/')) return true;
                    return !!findPlayerIframe() || !!findAllVideos().length;
                }

                function findPlayerIframe() {
                    const iframes = Array.from(document.querySelectorAll('iframe'));
                    return iframes.find(f => (f.title || '').toLowerCase().includes('premium667'))
                        || iframes.find(f => {
                            const s = (f.src || '').toLowerCase();
                            return s.includes('stream') || s.includes('embed') || s.includes('player') || s.includes('premium');
                        })
                        || iframes.find(f => {
                            const r = f.getBoundingClientRect();
                            return r.width > 200 && r.height > 150;
                        })
                        || null;
                }

                function findAllVideos() {
                    const vids = Array.from(document.querySelectorAll('video'));
                    document.querySelectorAll('iframe').forEach(f => {
                        try {
                            const doc = f.contentDocument || (f.contentWindow && f.contentWindow.document);
                            if (doc) vids.push(...Array.from(doc.querySelectorAll('video')));
                        } catch (e) {}
                    });
                    return vids;
                }

                function getAllDocs() {
                    const docs = [document];
                    document.querySelectorAll('iframe').forEach(f => {
                        try {
                            const doc = f.contentDocument || (f.contentWindow && f.contentWindow.document);
                            if (doc) docs.push(doc);
                        } catch (e) {}
                    });
                    return docs;
                }

                const style = document.createElement('style');
                style.id = 'tv-leanback-style';
                style.textContent = `
                    html, body {
                        background: #0B1120 !important;
                        overflow-x: hidden !important;
                    }
                    [data-tv-focus="1"] {
                        transition: transform 0.12s ease, box-shadow 0.12s ease, outline 0.12s ease !important;
                    }
                    [data-tv-focus="1"].tv-focused,
                    [data-tv-focus="1"]:focus {
                        outline: 3px solid #38BDF8 !important;
                        outline-offset: 2px !important;
                        box-shadow: 0 0 0 5px rgba(56, 189, 248, 0.35), 0 8px 24px rgba(0, 0, 0, 0.55) !important;
                        transform: scale(1.02) !important;
                        z-index: 50 !important;
                        position: relative !important;
                    }
                    body.tv-theater-mode {
                        overflow: hidden !important;
                        margin: 0 !important;
                        padding: 0 !important;
                        background: #000 !important;
                    }
                    body.tv-theater-mode .tv-player-fullscreen {
                        position: fixed !important;
                        top: 0 !important;
                        left: 0 !important;
                        width: 100vw !important;
                        height: 100vh !important;
                        max-width: 100vw !important;
                        max-height: 100vh !important;
                        margin: 0 !important;
                        padding: 0 !important;
                        border: none !important;
                        z-index: 2147483640 !important;
                        background: #000 !important;
                        object-fit: contain !important;
                    }
                    #tv-leanback-hud {
                        position: fixed;
                        left: 0;
                        right: 0;
                        bottom: 0;
                        padding: 28px 40px 26px;
                        background: linear-gradient(to top, rgba(0,0,0,0.92) 0%, rgba(0,0,0,0.68) 65%, rgba(0,0,0,0) 100%);
                        color: #F8FAFC;
                        font-family: system-ui, -apple-system, sans-serif;
                        z-index: 2147483647;
                        display: flex;
                        flex-direction: column;
                        gap: 14px;
                        opacity: 0;
                        pointer-events: none;
                        transition: opacity 0.2s ease;
                    }
                    #tv-leanback-hud.visible {
                        opacity: 1;
                        pointer-events: auto;
                    }
                    .tv-hud-top {
                        display: flex;
                        align-items: center;
                        gap: 12px;
                    }
                    .tv-live-badge {
                        background: #DC2626;
                        color: #FFFFFF;
                        font-size: 13px;
                        font-weight: 700;
                        letter-spacing: 0.06em;
                        padding: 4px 10px;
                        border-radius: 4px;
                        display: inline-flex;
                        align-items: center;
                        gap: 6px;
                    }
                    .tv-stream-title {
                        font-size: 20px;
                        font-weight: 600;
                        color: #F8FAFC;
                        white-space: nowrap;
                        overflow: hidden;
                        text-overflow: ellipsis;
                    }
                    .tv-hud-controls {
                        display: flex;
                        align-items: center;
                        gap: 12px;
                    }
                    .tv-hud-btn {
                        background: rgba(255, 255, 255, 0.14);
                        color: #F8FAFC;
                        border: 2px solid transparent;
                        border-radius: 999px;
                        padding: 9px 18px;
                        font-size: 15px;
                        font-weight: 600;
                        cursor: pointer;
                    }
                    .tv-hud-btn.active-server {
                        background: rgba(56, 189, 248, 0.25);
                        border-color: rgba(56, 189, 248, 0.7);
                    }
                    .tv-hud-btn.hud-focused {
                        background: #F8FAFC;
                        color: #0F172A;
                        border-color: #38BDF8;
                        box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.45);
                    }
                `;
                document.head.appendChild(style);

                let navIndex = 0;

                function getNavItems() {
                    const candidates = Array.from(document.querySelectorAll('a, button, [role="tab"], [role="button"], .game-row, .match-row'));
                    const seen = new Set();
                    const items = [];
                    for (const el of candidates) {
                        if (seen.has(el)) continue;
                        if (el.closest('#tv-leanback-hud')) continue;
                        const rect = el.getBoundingClientRect();
                        if (rect.width < 24 || rect.height < 18) continue;
                        const txt = (el.innerText || el.textContent || '').trim();
                        const href = (el.getAttribute('href') || '').toLowerCase();
                        const upper = txt.toUpperCase();
                        const isLeagueTab = LEAGUES.some(l => upper === l || upper.startsWith(l + ' ') || href.includes('/' + l.toLowerCase()));
                        const isGameRow = href.includes('/custom-stream/') || href.includes('/stream/') || el.classList.contains('game-row') || el.classList.contains('match-row');
                        if (isLeagueTab || isGameRow) {
                            seen.add(el);
                            el.setAttribute('data-tv-focus', '1');
                            el.setAttribute('tabindex', '0');
                            items.push(el);
                        }
                    }
                    return items;
                }

                function setScheduleFocus(idx) {
                    const items = getNavItems();
                    if (!items.length) return;
                    items.forEach(el => el.classList.remove('tv-focused'));
                    navIndex = Math.max(0, Math.min(idx, items.length - 1));
                    const target = items[navIndex];
                    target.classList.add('tv-focused');
                    try { target.focus({ preventScroll: true }); } catch (e) {}
                    target.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'smooth' });
                }

                function moveSpatial(dir) {
                    const items = getNavItems();
                    if (!items.length) return;
                    const cur = items[navIndex] || items[0];
                    const cr = cur.getBoundingClientRect();
                    const cx = cr.left + cr.width / 2;
                    const cy = cr.top + cr.height / 2;

                    let bestIdx = -1;
                    let bestScore = Infinity;

                    for (let i = 0; i < items.length; i++) {
                        if (i === navIndex) continue;
                        const r = items[i].getBoundingClientRect();
                        const x = r.left + r.width / 2;
                        const y = r.top + r.height / 2;
                        const dx = x - cx;
                        const dy = y - cy;

                        if (dir === 'LEFT' && dx >= -10) continue;
                        if (dir === 'RIGHT' && dx <= 10) continue;
                        if (dir === 'UP' && dy >= -10) continue;
                        if (dir === 'DOWN' && dy <= 10) continue;

                        const primary = (dir === 'LEFT' || dir === 'RIGHT') ? Math.abs(dx) : Math.abs(dy);
                        const secondary = (dir === 'LEFT' || dir === 'RIGHT') ? Math.abs(dy) : Math.abs(dx);
                        const score = primary + secondary * 2.5;
                        if (score < bestScore) {
                            bestScore = score;
                            bestIdx = i;
                        }
                    }

                    if (bestIdx !== -1) {
                        setScheduleFocus(bestIdx);
                    } else if (dir === 'DOWN' || dir === 'RIGHT') {
                        setScheduleFocus(Math.min(navIndex + 1, items.length - 1));
                    } else if (dir === 'UP' || dir === 'LEFT') {
                        setScheduleFocus(Math.max(navIndex - 1, 0));
                    }
                }

                let hudTimer = null;
                let hudVisible = false;
                let hudFocusIdx = 0;
                let activeServerNum = 1;
                let assumedPlaying = true;
                let assumedMuted = false;

                function hideDebugAndFloatingUnmute() {
                    getAllDocs().forEach(doc => {
                        const allEls = doc.querySelectorAll('div, section, aside, span, p');
                        for (const el of allEls) {
                            if (el.closest && el.closest('#tv-leanback-hud')) continue;
                            const t = (el.innerText || el.textContent || '');
                            if (t.length < 400 && (t.includes('Buffer Health') || t.includes('Dropped Frames'))) {
                                el.style.setProperty('display', 'none', 'important');
                                el.style.setProperty('visibility', 'hidden', 'important');
                            }
                        }

                        const btns = doc.querySelectorAll('button, div[role="button"], a, span');
                        for (const b of btns) {
                            if (b.closest && b.closest('#tv-leanback-hud')) continue;
                            const label = ((b.innerText || b.textContent || '') + ' ' + (b.getAttribute('aria-label') || '')).trim().toUpperCase();
                            if (label === 'UNMUTE' || label.includes('CLICK TO UNMUTE') || label.includes('TAP TO UNMUTE')) {
                                try { b.click(); } catch (e) {}
                            }
                        }
                    });
                }

                function applyTheaterAndUnmute() {
                    if (!isStreamPage()) {
                        document.body.classList.remove('tv-theater-mode');
                        return;
                    }
                    document.body.classList.add('tv-theater-mode');

                    const iframe = findPlayerIframe();
                    if (iframe) {
                        iframe.classList.add('tv-player-fullscreen');
                        let p = iframe.parentElement;
                        while (p && p !== document.body) {
                            p.style.setProperty('transform', 'none', 'important');
                            p.style.setProperty('overflow', 'visible', 'important');
                            p = p.parentElement;
                        }
                    }

                    const videos = findAllVideos();
                    videos.forEach(v => {
                        v.classList.add('tv-player-fullscreen');
                        v.style.setProperty('position', 'fixed', 'important');
                        v.style.setProperty('top', '0', 'important');
                        v.style.setProperty('left', '0', 'important');
                        v.style.setProperty('width', '100vw', 'important');
                        v.style.setProperty('height', '100vh', 'important');
                        v.style.setProperty('object-fit', 'contain', 'important');
                        v.style.setProperty('z-index', '2147483640', 'important');
                        if (!v.dataset.tvUnmuted) {
                            v.dataset.tvUnmuted = '1';
                            v.muted = false;
                            v.volume = 1.0;
                            if (v.paused) {
                                try { v.play().catch(() => {}); } catch (e) {}
                            }
                        }
                    });

                    hideDebugAndFloatingUnmute();
                }

                function getStreamTitle() {
                    const h = document.querySelector('h1, h2, .stream-title, .match-title');
                    const raw = (h && h.innerText ? h.innerText : document.title || 'Live Stream').trim();
                    return raw.replace(/\s*\|\s*StreamEast.*/i, '').trim() || 'Live Stream';
                }

                function ensureHud() {
                    let hud = document.getElementById('tv-leanback-hud');
                    if (hud) return hud;
                    hud = document.createElement('div');
                    hud.id = 'tv-leanback-hud';
                    hud.innerHTML = `
                        <div class="tv-hud-top">
                            <span class="tv-live-badge">• LIVE</span>
                            <span class="tv-stream-title" id="tv-hud-title"></span>
                        </div>
                        <div class="tv-hud-controls">
                            <button class="tv-hud-btn" data-hud-idx="0" id="tv-btn-play">Pause</button>
                            <button class="tv-hud-btn" data-hud-idx="1" id="tv-btn-mute">Mute</button>
                            <button class="tv-hud-btn" data-hud-idx="2" id="tv-btn-s1">Server 1</button>
                            <button class="tv-hud-btn" data-hud-idx="3" id="tv-btn-s2">Server 2</button>
                            <button class="tv-hud-btn" data-hud-idx="4" id="tv-btn-s3">Server 3</button>
                        </div>
                    `;
                    document.body.appendChild(hud);
                    return hud;
                }

                function updateHudUI() {
                    const hud = ensureHud();
                    const titleEl = document.getElementById('tv-hud-title');
                    if (titleEl) titleEl.textContent = getStreamTitle();

                    const vids = findAllVideos();
                    const isPlaying = vids.length ? !vids[0].paused : assumedPlaying;
                    const isMuted = vids.length ? vids[0].muted : assumedMuted;

                    const playBtn = document.getElementById('tv-btn-play');
                    const muteBtn = document.getElementById('tv-btn-mute');
                    if (playBtn) playBtn.textContent = isPlaying ? 'Pause' : 'Play';
                    if (muteBtn) muteBtn.textContent = isMuted ? 'Unmute' : 'Mute';

                    const btns = Array.from(hud.querySelectorAll('.tv-hud-btn'));
                    btns.forEach((b, i) => {
                        b.classList.toggle('hud-focused', i === hudFocusIdx);
                        b.classList.toggle('active-server', i === activeServerNum + 1);
                    });
                }

                function showHud(resetFocus) {
                    const hud = ensureHud();
                    if (resetFocus) hudFocusIdx = 0;
                    hudVisible = true;
                    updateHudUI();
                    hud.classList.add('visible');
                    clearTimeout(hudTimer);
                    hudTimer = setTimeout(() => hideHud(), 3500);
                }

                function hideHud() {
                    const hud = document.getElementById('tv-leanback-hud');
                    if (hud) hud.classList.remove('visible');
                    hudVisible = false;
                    clearTimeout(hudTimer);
                }

                function togglePlayPause() {
                    const vids = findAllVideos();
                    if (vids.length) {
                        vids.forEach(v => {
                            if (v.paused) v.play().catch(() => {});
                            else v.pause();
                        });
                    } else {
                        assumedPlaying = !assumedPlaying;
                        const iframe = findPlayerIframe();
                        if (iframe && iframe.contentWindow) {
                            try {
                                iframe.contentWindow.postMessage(
                                    JSON.stringify({ event: 'command', func: assumedPlaying ? 'playVideo' : 'pauseVideo' }),
                                    '*'
                                );
                            } catch (e) {}
                        }
                    }
                    showHud(false);
                }

                function toggleMute() {
                    const vids = findAllVideos();
                    if (vids.length) {
                        const targetMuted = !vids[0].muted;
                        vids.forEach(v => {
                            v.muted = targetMuted;
                            if (!targetMuted) v.volume = 1.0;
                        });
                    } else {
                        assumedMuted = !assumedMuted;
                        const iframe = findPlayerIframe();
                        if (iframe && iframe.contentWindow) {
                            try {
                                iframe.contentWindow.postMessage(
                                    JSON.stringify({ event: 'command', func: assumedMuted ? 'mute' : 'unMute' }),
                                    '*'
                                );
                            } catch (e) {}
                        }
                    }
                    showHud(false);
                }

                function switchServer(num) {
                    activeServerNum = num;
                    const candidates = Array.from(document.querySelectorAll('a, button, [role="button"]')).filter(el => {
                        if (el.closest('#tv-leanback-hud')) return false;
                        const t = (el.innerText || el.textContent || '').trim().toLowerCase();
                        return t.includes('server') || t.includes('stream ' + num) || t === 'hd ' + num || t === String(num);
                    });
                    const exact = candidates.find(el => {
                        const t = (el.innerText || el.textContent || '').trim().toLowerCase();
                        return t.includes('server ' + num) || t.includes('server' + num) || t.includes('stream ' + num);
                    }) || candidates[num - 1];

                    if (exact) {
                        try { exact.click(); } catch (e) {}
                        setTimeout(applyTheaterAndUnmute, 600);
                    }
                    showHud(false);
                }

                function activateHudButton() {
                    if (hudFocusIdx === 0) togglePlayPause();
                    else if (hudFocusIdx === 1) toggleMute();
                    else if (hudFocusIdx === 2) switchServer(1);
                    else if (hudFocusIdx === 3) switchServer(2);
                    else if (hudFocusIdx === 4) switchServer(3);
                }

                window.__tvHandleKey = function(key) {
                    if (isStreamPage()) {
                        if (key === 'PLAY_PAUSE') {
                            togglePlayPause();
                            return;
                        }
                        if (key === 'CENTER') {
                            if (!hudVisible) {
                                showHud(true);
                            } else {
                                activateHudButton();
                            }
                            return;
                        }
                        if (key === 'LEFT') {
                            if (!hudVisible) showHud(true);
                            else {
                                hudFocusIdx = (hudFocusIdx + 4) % 5;
                                showHud(false);
                            }
                            return;
                        }
                        if (key === 'RIGHT') {
                            if (!hudVisible) showHud(true);
                            else {
                                hudFocusIdx = (hudFocusIdx + 1) % 5;
                                showHud(false);
                            }
                            return;
                        }
                        if (key === 'UP' || key === 'DOWN') {
                            if (!hudVisible) showHud(true);
                            else showHud(false);
                            return;
                        }
                    } else {
                        if (key === 'UP' || key === 'DOWN' || key === 'LEFT' || key === 'RIGHT') {
                            moveSpatial(key);
                        } else if (key === 'CENTER') {
                            const items = getNavItems();
                            const target = items[navIndex];
                            if (target) target.click();
                        }
                    }
                };

                window.__tvHandleBack = function() {
                    if (isStreamPage() && hudVisible) {
                        hideHud();
                        return true;
                    }
                    return false;
                };

                window.__tvRefresh = function() {
                    if (isStreamPage()) {
                        applyTheaterAndUnmute();
                    } else {
                        document.body.classList.remove('tv-theater-mode');
                        hideHud();
                        setScheduleFocus(navIndex);
                    }
                };

                window.__tvRefresh();
                let ticks = 0;
                const bootInterval = setInterval(() => {
                    ticks++;
                    if (isStreamPage()) applyTheaterAndUnmute();
                    if (ticks >= 25) clearInterval(bootInterval);
                }, 600);
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }
}
