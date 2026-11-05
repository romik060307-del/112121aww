package com.rk.ohlisocial;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int WEB_PERMISSION_REQUEST = 701;
    private static final int FILE_REQUEST = 702;

    private WebView web;
    private FrameLayout root;
    private View splash;
    private View offlineOverlay;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingWebPermission;
    private String[] pendingRuntimePermissions = new String[0];
    private boolean pageLoaded;
    private OnBackInvokedCallback backInvokedCallback;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(16, 45, 43));
        getWindow().setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        root = new FrameLayout(this);
        web = createWebView();
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        splash = makeSplash();
        root.addView(splash, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        // Android 13+ uses the predictive-back dispatcher. Keep the same in-app
        // navigation behavior as the legacy Back button on older Android versions.
        if (Build.VERSION.SDK_INT >= 33) {
            backInvokedCallback = this::handleBackNavigation;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, backInvokedCallback);
        }

        web.loadUrl(BuildConfig.OHLI_URL);
    }

    private WebView createWebView() {
        WebView view = new WebView(this);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        // WebRTC camera/microphone capture is handled through WebChromeClient
        // and Android runtime permissions below.
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(view, true);

        view.setBackgroundColor(Color.rgb(247, 250, 249));
        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                return routeNavigation(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return routeNavigation(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                pageLoaded = true;
                hideSplash();
                hideOffline();
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    String detail = error != null && error.getDescription() != null
                            ? error.getDescription().toString() : "Periksa koneksi internet kamu.";
                    showOffline(detail);
                }
            }
        });

        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int progress) {
                // The native splash remains visible until the main document finishes loading.
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                handleWebPermissionRequest(request);
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebPermission == request) {
                    pendingWebPermission = null;
                    pendingRuntimePermissions = new String[0];
                }
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent picker = params.createIntent();
                    picker.addCategory(Intent.CATEGORY_OPENABLE);
                    if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    }
                    startActivityForResult(picker, FILE_REQUEST);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "Pemilih file tidak tersedia", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });
        return view;
    }

    /** Keep app navigation inside the configured OHLI HTTPS origin only. */
    private boolean routeNavigation(Uri uri) {
        if (uri == null) return true;
        if (isTrustedOrigin(uri)) return false;

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if ("https".equals(scheme) || "mailto".equals(scheme) || "tel".equals(scheme)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (Exception ignored) {
                Toast.makeText(this, "Tidak ada aplikasi untuk membuka tautan ini", Toast.LENGTH_SHORT).show();
            }
        }
        // Block file:, content:, javascript:, intent: and all other untrusted schemes.
        return true;
    }

    private boolean isTrustedOrigin(Uri candidate) {
        Uri trusted = Uri.parse(BuildConfig.OHLI_URL);
        String trustedScheme = trusted.getScheme();
        String trustedHost = trusted.getHost();
        if (trustedScheme == null || trustedHost == null || candidate.getScheme() == null || candidate.getHost() == null) {
            return false;
        }
        int trustedPort = trusted.getPort() == -1 ? ("https".equalsIgnoreCase(trustedScheme) ? 443 : 80) : trusted.getPort();
        int candidatePort = candidate.getPort() == -1 ? ("https".equalsIgnoreCase(candidate.getScheme()) ? 443 : 80) : candidate.getPort();
        return trustedScheme.equalsIgnoreCase(candidate.getScheme())
                && trustedHost.equalsIgnoreCase(candidate.getHost())
                && trustedPort == candidatePort;
    }

    private void handleWebPermissionRequest(PermissionRequest request) {
        // getUserMedia is allowed only from the active HTTPS page (or the
        // configured HTTPS entry point). This also supports a legitimate HTTPS
        // redirect without granting media access to unrelated web origins.
        if (!isAllowedMediaOrigin(request.getOrigin())) {
            denyWebPermissionSafely(request);
            return;
        }

        List<String> needed = new ArrayList<>();
        String[] resources = request.getResources();
        for (String resource : resources) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                if (!needed.contains(Manifest.permission.CAMERA)) {
                    needed.add(Manifest.permission.CAMERA);
                }
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                if (!needed.contains(Manifest.permission.RECORD_AUDIO)) {
                    needed.add(Manifest.permission.RECORD_AUDIO);
                }
            } else {
                // Do not silently grant WebView resources that are not needed
                // by OHLI's camera/microphone features.
                denyWebPermissionSafely(request);
                return;
            }
        }

        if (needed.isEmpty()) {
            denyWebPermissionSafely(request);
            return;
        }

        List<String> missing = new ArrayList<>();
        for (String permission : needed) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }

        if (missing.isEmpty()) {
            grantWebPermissionSafely(request);
        } else {
            // There should be only one outstanding WebView media prompt at a
            // time. Finish the old request rather than leaving it hanging.
            if (pendingWebPermission != null && pendingWebPermission != request) {
                denyWebPermissionSafely(pendingWebPermission);
            }
            pendingWebPermission = request;
            pendingRuntimePermissions = missing.toArray(new String[0]);
            requestPermissions(pendingRuntimePermissions, WEB_PERMISSION_REQUEST);
        }
    }

    private boolean isAllowedMediaOrigin(Uri origin) {
        if (origin == null || origin.getScheme() == null
                || !"https".equalsIgnoreCase(origin.getScheme())
                || origin.getHost() == null) {
            return false;
        }

        Uri current = null;
        if (web != null && web.getUrl() != null) {
            current = Uri.parse(web.getUrl());
        }
        Uri configured = Uri.parse(BuildConfig.OHLI_URL);
        return sameOrigin(origin, current) || sameOrigin(origin, configured);
    }

    private boolean sameOrigin(Uri first, Uri second) {
        if (first == null || second == null || first.getScheme() == null
                || second.getScheme() == null || first.getHost() == null
                || second.getHost() == null) {
            return false;
        }
        int firstPort = first.getPort() == -1 ? 443 : first.getPort();
        int secondPort = second.getPort() == -1 ? 443 : second.getPort();
        return "https".equalsIgnoreCase(first.getScheme())
                && "https".equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && firstPort == secondPort;
    }

    private void grantWebPermissionSafely(PermissionRequest request) {
        try {
            request.grant(request.getResources());
        } catch (Exception ignored) {
            // The website may have navigated away or canceled the request.
        }
    }

    private void denyWebPermissionSafely(PermissionRequest request) {
        try {
            request.deny();
        } catch (Exception ignored) {
            // The request may already have been canceled by Chromium.
        }
    }

    private View makeSplash() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.rgb(247, 250, 249));

        TextView mark = new TextView(this);
        mark.setText("OHLI");
        mark.setTextColor(Color.rgb(16, 75, 66));
        mark.setTextSize(38);
        mark.setTypeface(null, 1);
        mark.setGravity(Gravity.CENTER);
        box.addView(mark);

        TextView sub = new TextView(this);
        sub.setText("SOCIAL");
        sub.setTextColor(Color.rgb(22, 138, 112));
        sub.setTextSize(13);
        sub.setLetterSpacing(.28f);
        sub.setGravity(Gravity.CENTER);
        box.addView(sub);

        ProgressBar spinner = new ProgressBar(this);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        spinnerParams.topMargin = dp(26);
        box.addView(spinner, spinnerParams);

        TextView caption = new TextView(this);
        caption.setText("Menghubungkan ke OHLI Social");
        caption.setTextColor(Color.rgb(105, 125, 120));
        caption.setTextSize(13);
        caption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-2, -2);
        captionParams.topMargin = dp(14);
        box.addView(caption, captionParams);
        return box;
    }

    private void hideSplash() {
        if (splash != null) splash.setVisibility(View.GONE);
    }

    private void showOffline(String detail) {
        hideSplash();
        hideOffline();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(28), dp(28), dp(28), dp(28));
        panel.setBackgroundColor(Color.rgb(247, 250, 249));

        TextView title = new TextView(this);
        title.setText("OHLI Social belum terhubung");
        title.setTextColor(Color.rgb(16, 75, 66));
        title.setTextSize(21);
        title.setTypeface(null, 1);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);

        TextView message = new TextView(this);
        message.setText("Periksa internet atau pastikan server OHLI sedang aktif, lalu coba lagi.\n\n" + detail);
        message.setTextColor(Color.rgb(85, 105, 100));
        message.setTextSize(14);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, dp(12), 0, dp(18));
        panel.addView(message);

        Button retry = new Button(this);
        retry.setText("Coba lagi");
        retry.setOnClickListener(v -> {
            pageLoaded = false;
            hideOffline();
            if (splash != null) splash.setVisibility(View.VISIBLE);
            if (web != null) web.loadUrl(BuildConfig.OHLI_URL);
        });
        panel.addView(retry, new LinearLayout.LayoutParams(-2, -2));

        offlineOverlay = panel;
        root.addView(offlineOverlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private void hideOffline() {
        if (offlineOverlay != null && root != null) {
            root.removeView(offlineOverlay);
            offlineOverlay = null;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WEB_PERMISSION_REQUEST && pendingWebPermission != null) {
            boolean allGranted = pendingRuntimePermissions.length > 0;
            for (String required : pendingRuntimePermissions) {
                boolean granted = false;
                for (int i = 0; i < permissions.length; i++) {
                    if (required.equals(permissions[i]) && i < grantResults.length
                            && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        granted = true;
                        break;
                    }
                }
                if (!granted) allGranted = false;
            }
            if (allGranted && isAllowedMediaOrigin(pendingWebPermission.getOrigin())) {
                grantWebPermissionSafely(pendingWebPermission);
            } else {
                denyWebPermissionSafely(pendingWebPermission);
            }
            pendingWebPermission = null;
            pendingRuntimePermissions = new String[0];
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQUEST && fileCallback != null) {
            Uri[] selected = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    selected = new Uri[count];
                    for (int i = 0; i < count; i++) selected[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    selected = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(selected);
            fileCallback = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // Android 12 and below enter here. Android 13+ is registered above.
        handleBackNavigation();
    }

    private void handleBackNavigation() {
        if (offlineOverlay != null) {
            hideOffline();
            return;
        }
        if (web == null) return;

        // Respect real browser history first (including pages pushed by the
        // web app). If this is a tab/view inside a single-page app, switch to
        // its Beranda control instead of closing the Android application.
        if (web.canGoBack()) {
            web.goBack();
            return;
        }
        returnToHome();
    }

    private void returnToHome() {
        if (web == null) return;
        final String homeScript = "(function(){"
                + "var names=['beranda','home','feed','beranda utama'];"
                + "var selectors='[data-tab=\"home\"],[data-nav=\"home\"],[data-route=\"home\"],#homeTab,#home-tab,[aria-label=\"Beranda\"],[aria-label=\"Home\"],[title=\"Beranda\"],[title=\"Home\"]';"
                + "var visible=function(x){return !!(x&&(x.offsetWidth||x.offsetHeight||x.getClientRects().length));};"
                + "var matches=document.querySelectorAll(selectors),el=null,i;"
                + "for(i=0;i<matches.length;i++){if(visible(matches[i])){el=matches[i];break;}}"
                + "if(!el){var all=document.querySelectorAll('button,[role=button],a');"
                + "for(i=0;i<all.length;i++){var x=all[i];var t=(x.getAttribute('aria-label')||x.getAttribute('title')||x.innerText||x.textContent||'').trim().toLowerCase();"
                + "if(visible(x)&&names.indexOf(t)!==-1){el=x;break;}}}"
                + "if(el){var c=(el.getAttribute('aria-current')||'').toLowerCase();"
                + "var cls=(typeof el.className==='string'?el.className:'').toLowerCase();"
                + "if(c==='page'||c==='true'||el.getAttribute('aria-selected')==='true'||/(^|\\s)(active|selected)(\\s|$)/.test(cls))return 'already-home';"
                + "el.click();return 'clicked';}return 'reload';})()";

        web.evaluateJavascript(homeScript, value -> {
            if (web == null) return;
            if (value != null && value.contains("already-home")) {
                Toast.makeText(MainActivity.this, "Kamu sudah berada di Beranda", Toast.LENGTH_SHORT).show();
            } else if (value == null || value.contains("reload")) {
                // Reloading the configured entry URL restores the app's default
                // home view while retaining its WebView cookies/local storage.
                web.loadUrl(BuildConfig.OHLI_URL);
            }
            // 'clicked' means the website's own Beranda tab has been selected.
        });
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backInvokedCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backInvokedCallback);
            backInvokedCallback = null;
        }
        if (pendingWebPermission != null) denyWebPermissionSafely(pendingWebPermission);
        pendingWebPermission = null;
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        fileCallback = null;
        if (web != null) {
            web.stopLoading();
            web.loadUrl("about:blank");
            web.removeAllViews();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
