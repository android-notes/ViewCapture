package io.github.viewcapture.demo;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;

/** Deterministic host for device tests. */
public final class CaptureTestActivity extends Activity {
    SceneContent scene;
    ScrollView scroll;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        scroll = new ScrollView(this);
        setContentView(scroll);
        scene =
                new SceneContent(
                        this,
                        getIntent().getIntExtra("scene", 0),
                        getIntent().getBooleanExtra("long", true),
                        getIntent().getIntExtra("groups", 1));
        scroll.addView(scene.target, new ScrollView.LayoutParams(-1, -2));
    }

    @Override
    protected void onDestroy() {
        scroll.removeAllViews();
        scene.close();
        super.onDestroy();
    }
}
