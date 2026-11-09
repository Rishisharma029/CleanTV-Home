package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.util.Locale;

/**
 * CleanTV Shield Streamer Activity:
 * High-performance, TV remote-controlled Web Player with built-in Brave-style
 * video ad-interception and scriptlet fast-forwarding engine.
 * Eliminates SSAI and video ads across Prime Video, YouTube, and web streaming.
 */
public class CleanTvShieldStreamerActivity extends Activity {

    public static final String EXTRA_URL = "target_url";
    public static final String DEFAULT_URL = "https://www.primevideo.com";

    private WebView webView;
    private FrameLayout rootLayout;
    private LinearLayout topBar;
    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideTopBarRunnable = () -> {
        if (topBar != null) {
            topBar.animate().alpha(0f).translationY(-50f).setDuration(250).withEndAction(() -> topBar.setVisibility(View.GONE)).start();
        }
    };

    // Brave-Style Video Ad-Skipper & Scriptlet Engine
    private static final String SCRIPTLET_BRAVE_SHIELD =
            "(function() {" +
            "  if (window.__cleantv_shield_active) return;" +
            "  window.__cleantv_shield_active = true;" +
            "  console.log('[CleanTV Shield] Brave-Style Video Ad Engine Activated');" +
            "  var origFetch = window.fetch;" +
            "  if (origFetch) {" +
            "    window.fetch = function(input, init) {" +
            "      var url = (typeof input === 'string') ? input : (input && input.url ? input.url : '');" +
            "      if (url && (url.indexOf('amazon-adsystem') !== -1 || url.indexOf('device-metrics') !== -1 || url.indexOf('fls-na.amazon') !== -1 || url.indexOf('doubleclick') !== -1 || url.indexOf('/ad/') !== -1 || url.indexOf('ad_break') !== -1)) {" +
            "        return Promise.resolve(new Response('{\"adBreak\":false,\"adPods\":[],\"status\":\"ok\"}', { status: 200, headers: {'Content-Type': 'application/json'} }));" +
            "      }" +
            "      return origFetch.apply(this, arguments);" +
            "    };" +
            "  }" +
            "  if (navigator.sendBeacon) {" +
            "    navigator.sendBeacon = function() { return true; };" +
            "  }" +
            "  setInterval(function() {" +
            "    try {" +
            "      var videos = document.querySelectorAll('video');" +
            "      for (var i = 0; i < videos.length; i++) {" +
            "        var v = videos[i];" +
            "        var isAd = false;" +
            "        var adContainer = document.querySelector('.ad-showing, .ad-interrupting, .ad-container, [class*=\"adMarker\"], [class*=\"ad-display\"], [class*=\"adOverlay\"], .video-ads, [data-ad-playing=\"true\"]');" +
            "        if (adContainer) isAd = true;" +
            "        if (!isAd && !v.controls && v.duration > 0 && v.duration < 120 && v.currentTime > 0) {" +
            "          var bodyText = document.body ? document.body.innerText : '';" +
            "          if (bodyText.indexOf('Ad ') !== -1 || bodyText.indexOf('Sponsored') !== -1 || bodyText.indexOf('Commercial') !== -1) {" +
            "            isAd = true;" +
            "          }" +
            "        }" +
            "        if (isAd) {" +
            "          v.muted = true;" +
            "          v.playbackRate = 16.0;" +
            "          if (isFinite(v.duration) && v.duration > 0) {" +
            "            v.currentTime = v.duration;" +
            "          }" +
            "        }" +
            "      }" +
            "      var skipSelectors = ['.ytp-ad-skip-button', '.ytp-ad-skip-button-modern', '.skip-button', '[class*=\"skipAd\"]', '[class*=\"skip-button\"]', '[aria-label*=\"Skip\"]', '.atvwebplayersdk-ad-skip-button', '.ad-skip-btn', '[id*=\"skipAd\"]'];" +
            "      for (var s = 0; s < skipSelectors.length; s++) {" +
            "        var btn = document.querySelector(skipSelectors[s]);" +
            "        if (btn && btn.offsetParent !== null) {" +
            "          btn.click();" +
            "        }" +
            "      }" +
            "    } catch(e) {}" +
            "  }, 80);" +
            "})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);

        // Modern TV desktop user agent for smooth HTML5 video player delivery
        settings.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 CleanTVShield/1.0");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    String url = request.getUrl().toString().toLowerCase(Locale.ROOT);
                    if (isAdOrTelemetryUrl(url)) {
                        // Drop ad network request and return mock empty response
                        return new WebResourceResponse("application/json", "UTF-8",
                                new ByteArrayInputStream("{}".getBytes()));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                injectScriptlet();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectScriptlet();
            }
        });

        rootLayout.addView(webView);

        // Build overlay TopBar with quick navigation
        buildTopBar();
        rootLayout.addView(topBar);

        setContentView(rootLayout);

        String initialUrl = getIntent().getStringExtra(EXTRA_URL);
        if (initialUrl == null || initialUrl.trim().isEmpty()) {
            initialUrl = DEFAULT_URL;
        }

        Toast.makeText(this, "🛡️ CleanTV Brave Shield Active", Toast.LENGTH_SHORT).show();
        webView.loadUrl(initialUrl);

        showTopBarBriefly();
    }

    private void injectScriptlet() {
        if (webView != null) {
            webView.evaluateJavascript(SCRIPTLET_BRAVE_SHIELD, null);
        }
    }

    private boolean isAdOrTelemetryUrl(String url) {
        return url.contains("amazon-adsystem.com") ||
                url.contains("device-metrics-us.amazon.com") ||
                url.contains("fls-na.amazon.com") ||
                url.contains("fls-eu.amazon.com") ||
                url.contains("aax.amazon-adsystem.com") ||
                url.contains("mads.amazon-adsystem.com") ||
                url.contains("c.amazon-adsystem.com") ||
                url.contains("googleads.g.doubleclick.net") ||
                url.contains("ad.doubleclick.net") ||
                url.contains("pagead2.googlesyndication.com") ||
                url.contains("criteo.com") ||
                url.contains("taboola.com") ||
                url.contains("outbrain.com") ||
                url.contains("moatads.com");
    }

    private void buildTopBar() {
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(Color.parseColor("#E6121418"));
        topBar.setPadding(32, 16, 32, 16);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topBar.setLayoutParams(lp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("🛡️ CleanTV Shield Streamer");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(16f);
        topBar.addView(tvTitle);

        View spacer = new View(this);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, 1, 1f);
        topBar.addView(spacer, spLp);

        addQuickButton("Prime Video", () -> webView.loadUrl("https://www.primevideo.com"));
        addQuickButton("YouTube", () -> webView.loadUrl("https://www.youtube.com/tv"));
        addQuickButton("Netflix", () -> webView.loadUrl("https://www.netflix.com"));
        addQuickButton("Exit", this::finish);
    }

    private void addQuickButton(String title, Runnable onClick) {
        TextView btn = new TextView(this);
        btn.setText(title);
        btn.setTextColor(Color.parseColor("#49B6FF"));
        btn.setTextSize(14f);
        btn.setPadding(24, 12, 24, 12);
        btn.setFocusable(true);
        btn.setClickable(true);
        btn.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = 16;
        btn.setLayoutParams(lp);

        btn.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackgroundColor(hasFocus ? Color.parseColor("#FF49B6FF") : Color.parseColor("#33FFFFFF"));
            ((TextView) v).setTextColor(hasFocus ? Color.BLACK : Color.parseColor("#49B6FF"));
        });

        btn.setOnClickListener(v -> onClick.run());
        topBar.addView(btn);
    }

    private void showTopBarBriefly() {
        if (topBar != null) {
            topBar.setVisibility(View.VISIBLE);
            topBar.setAlpha(1f);
            topBar.setTranslationY(0f);
            hideHandler.removeCallbacks(hideTopBarRunnable);
            hideHandler.postDelayed(hideTopBarRunnable, 4000);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
            showTopBarBriefly();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (topBar.getVisibility() == View.VISIBLE) {
                topBar.setVisibility(View.GONE);
                return true;
            }
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
            finish();
            return true;
        }

        // TV Remote Navigation
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (topBar.getVisibility() == View.VISIBLE && topBar.hasFocus()) {
                webView.requestFocus();
                return true;
            }
            webView.scrollBy(0, 150);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (webView.getScrollY() <= 10) {
                showTopBarBriefly();
                if (topBar.getChildCount() > 2) {
                    topBar.getChildAt(2).requestFocus();
                }
                return true;
            }
            webView.scrollBy(0, -150);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (webView != null) {
                webView.evaluateJavascript(
                        "(function(){ var v = document.querySelector('video'); if (v) { v.paused ? v.play() : v.pause(); } })();", null);
            }
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
