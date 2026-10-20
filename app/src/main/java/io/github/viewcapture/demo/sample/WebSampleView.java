package io.github.viewcapture.demo.sample;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import io.github.viewcapture.demo.R;

public final class WebSampleView extends FrameLayout implements AutoCloseable {
    private final WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    public WebSampleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        inflate(context, R.layout.view_web_sample, this);
        web = findViewById(R.id.web_content);
        web.setWebViewClient(new WebViewClient());
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setUseWideViewPort(true);
        web.getSettings().setLoadWithOverviewMode(true);
        web.loadUrl("https://www.douyin.com/");
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (web == null) {
            return;
        }
        if (visibility == VISIBLE) {
            web.onResume();
        } else {
            web.onPause();
        }
    }

    @Override
    public void close() {
        removeView(web);
        web.stopLoading();
        web.destroy();
    }
}
