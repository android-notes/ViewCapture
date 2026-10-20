package io.github.viewcapture.demo;

import android.view.MotionEvent;

/** Both panes use target-local pixels and equal viewport heights. */
final class ScrollSynchronizer {
    private final CaptureScrollView source;
    private final BitmapCompareView bitmap;
    private boolean enabled = true, updating;

    ScrollSynchronizer(CaptureScrollView source, BitmapCompareView bitmap) {
        this.source = source;
        this.bitmap = bitmap;
        source.setScrollChanged(() -> sync(source, bitmap));
        bitmap.setScrollChanged(() -> sync(bitmap, source));
        source.setOnTouchListener((view, event) -> stopOther(event, bitmap));
        bitmap.setOnTouchListener((view, event) -> stopOther(event, source));
        bitmap.getViewTreeObserver().addOnGlobalLayoutListener(this::syncFromSource);
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        source.stopScroll();
        bitmap.stopScroll();
        if (enabled) {
            syncFromSource();
        }
    }

    void syncFromSource() {
        sync(source, bitmap);
    }

    private boolean stopOther(MotionEvent event, CaptureScrollView other) {
        if (enabled && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            other.stopScroll();
        }
        return false;
    }

    private void sync(CaptureScrollView from, CaptureScrollView to) {
        if (!enabled || updating || !bitmap.hasBitmap()) {
            return;
        }
        updating = true;
        try {
            to.scrollTo(0, from.getScrollY());
        } finally {
            updating = false;
        }
    }
}
