/*
 * Copyright (c) 2017. Kaede (kidhaibara@gmail.com) All Rights Reserved.
 */

package me.kaede.feya.webview;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.JsPromptResult;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import butterknife.BindView;
import butterknife.ButterKnife;
import me.kaede.feya.BaseActivity;
import me.kaede.feya.BuildConfig;
import me.kaede.feya.R;
import me.kaede.feya.StopWatchEh;

public class WebActivity extends BaseActivity {
    static final String TAG = "WebActivity";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    @BindView(R.id.toolbar)
    protected Toolbar toolbar;
    @BindView(R.id.webview)
    protected WebView webView;

    private JavaScriptBridge mJsbApp;
    private StopWatchEh stopWatch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);
        prepareWebView();
        stopWatch = new StopWatchEh();
        stopWatch.start("load url");
        webView.loadUrl("http://www.bilibili.com/html/2233birthday-test-m.html");
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isFinishing()) {
            webView.loadUrl("");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mJsbApp != null) {
            mJsbApp.onActivityDestoryed();
            mJsbApp = null;
        }
        webView.destroy();
        webView = null;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    protected void prepareWebView() {
        WebSettings settings = webView.getSettings();
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        String originalUA = settings.getUserAgentString();
        settings.setUserAgentString(originalUA + " FeyaApp/" + BuildConfig.VERSION_CODE);
        if (DEBUG) settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (DEBUG) {
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                settings.setDatabasePath("/data/data/" + getPackageName() + "/databases/");
            }
            mJsbApp = createAppMainJavaScriptBridge();
            if (mJsbApp != null) {
                webView.removeJavascriptInterface("app");
                webView.addJavascriptInterface(mJsbApp, "app");
            }
        }
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(DEBUG || BuildConfig.DEBUG);
        }
        webView.setWebChromeClient(createWebChromeClient());
        webView.setWebViewClient(createWebViewClient());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            webView.removeJavascriptInterface("searchBoxJavaBridge_");
            webView.removeJavascriptInterface("accessibility");
            webView.removeJavascriptInterface("accessibilityTraversal");
        }
    }

    @Nullable
    protected JavaScriptBridge createAppMainJavaScriptBridge() {
        return new JavaScriptBridge(this);
    }

    static final int REQUEST_SELECT_FILE = 0xff;

    protected WebChromeClient createWebChromeClient() {
        return new WebChromeClient() {

            @Override
            protected boolean onShowFileChooser(Intent intent) {
                try {
                    startActivityForResult(intent, REQUEST_SELECT_FILE);
                    return true;
                } catch (ActivityNotFoundException e) {
                    return false;
                }
            }

            @NonNull
            @Override
            protected Context getContext() {
                return getApplicationContext();
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                stopWatch.split("visible");
                Log.i(TAG, "[onReceivedTitle] title = " + title);
                getSupportActionBar().setTitle(title);

                // 注入JS脚本, 监听 DOMContentLoaded 事件
                JavaScriptInjector.injectJs(view);
            }


            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                Log.d(TAG, "[onJsPrompt] message = " + message);
                if (message != null) {
                    String[] strs = message.split(":");
                    if (2 == strs.length) {
                        if ("domc".equals(strs[0])) {
                            stopWatch.split("dom_loaded", Long.valueOf(strs[1].trim()));
                            Log.i(TAG, "[onJsPrompt] DOMContentLoaded time = " + strs[1].trim());
                        } else if ("firstscreen".equals(strs[0])) {
                            stopWatch.split("first_screen", Long.valueOf(strs[1].trim()));
                            Log.i(TAG, "[onJsPrompt] first screen time = " + strs[1].trim());
                        } else if ("load".equals(strs[0])) {
                            stopWatch.split("page_loaded", Long.valueOf(strs[1].trim()));
                            Log.i(TAG, "[onJsPrompt] page loaded time = " + strs[1].trim());
                        }
                    }
                }

                result.confirm(defaultValue);
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
            }
        };
    }

    private WebViewClient createWebViewClient() {
        return new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                stopWatch.split("shouldOverrideUrlLoading");
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                Uri parsedUri = Uri.parse(url);
                if (url.startsWith("feya://")) {
                    intent = new Intent(Intent.ACTION_VIEW, parsedUri);
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    intent.setPackage(getApplicationContext().getPackageName());
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }
                    return true;
                } else if (url.startsWith("mailto:")) {
                    intent.setData(parsedUri);
                    startActivity(intent);
                    return true;
                } else if (url.startsWith("tel:")) {
                    return loadPhoneCallUrl(url);
                } else {
                    return loadHttpScheme(parsedUri) || onOverrideUrlLoading(view, parsedUri);
                }
            }

            private boolean loadPhoneCallUrl(String url) {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
                    startActivity(intent);
                    return true;
                } catch (Exception ignore) {
                    return false;
                }

            }

            private boolean loadHttpScheme(Uri parsedUri) {
                // Other custom scheme
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                stopWatch.split("page_started");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                String msg = stopWatch.end("page_finished");
                Log.i(TAG, msg);

                // print tag
                Log.i(TAG, "开始 = " + stopWatch.getTag("page_started") +
                        ", 白屏 = " + stopWatch.getTag("visible") +
                        ", DOM = " + stopWatch.getTag("dom_loaded") +
                        ", 首屏 = " + stopWatch.getTag("first_screen") +
                        ", 整屏 = " + stopWatch.getTag("page_loaded"));
            }
        };
    }

    protected boolean onOverrideUrlLoading(WebView view, Uri uri) {
        // override by children
        return false;
    }
}
