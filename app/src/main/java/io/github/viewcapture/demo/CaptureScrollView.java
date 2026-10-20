package io.github.viewcapture.demo;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ScrollView;

/** Locks user scrolling while the PixelCopy job moves the viewport programmatically. */
public class CaptureScrollView extends ScrollView {
    private boolean captureLocked;
    private Runnable scrollChanged;

    public CaptureScrollView(Context context) {
        this(context, null);
    }

    public CaptureScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setCaptureLocked(boolean locked) {
        captureLocked = locked;
    }

    void stopScroll() {
        fling(0);
        computeScroll();
    }

    void setScrollChanged(Runnable listener) {
        scrollChanged = listener;
    }

    @Override
    protected void onScrollChanged(int x, int y, int oldX, int oldY) {
        super.onScrollChanged(x, y, oldX, oldY);
        if (scrollChanged != null) {
            scrollChanged.run();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return captureLocked || super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return captureLocked || super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
