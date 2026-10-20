package io.github.viewcapture.demo;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Deterministic pixel probes for capture tests. */
final class ContentValidation {
    static final class Probe {
        final String name;
        final int x, y, color;

        Probe(String name, int x, int y, int color) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }

    static List<Probe> probes(SceneContent scene) {
        List<Probe> probes = new ArrayList<>();
        add(probes, scene, scene.topMarker, "START", SceneContent.TOP_COLOR);
        add(probes, scene, scene.bottomMarker, "END", SceneContent.BOTTOM_COLOR);
        if (scene.texture != null) {
            add(probes, scene, scene.texture, "TextureView", SceneContent.TEXTURE_COLOR);
        }
        if (scene.surface != null) {
            add(probes, scene, scene.surface, "SurfaceView", SceneContent.SURFACE_COLOR);
        }
        if (scene.bitmapView != null) {
            add(probes, scene, scene.bitmapView, "HARDWARE Bitmap", SceneContent.BITMAP_COLOR);
        }
        return probes;
    }

    private static void add(
            List<Probe> probes, SceneContent scene, View view, String name, int color) {
        Rect bounds = new Rect(0, 0, view.getWidth(), view.getHeight());
        scene.target.offsetDescendantRectToMyCoords(view, bounds);
        probes.add(
                new Probe(
                        name,
                        bounds.right - Math.max(3, bounds.width() / 20),
                        bounds.top + bounds.height() * 3 / 4,
                        color));
    }

    static String validate(CaptureSample sample, List<Probe> probes, boolean effects) {
        if (!sample.status.equals("OK") || sample.bitmap == null) {
            return "NO_IMAGE";
        }
        Bitmap readable = null;
        try {
            readable = sample.readableBitmap();
            List<String> missing = new ArrayList<>();
            for (Probe probe : probes) {
                if (probe.x < 0
                        || probe.y < 0
                        || probe.x >= readable.getWidth()
                        || probe.y >= readable.getHeight()
                        || !similar(readable.getPixel(probe.x, probe.y), probe.color)) {
                    missing.add(probe.name);
                }
            }
            return missing.isEmpty()
                    ? (effects ? "PROBES_PASS_VISUAL_REVIEW_REQUIRED" : "PROBES_PASS")
                    : "CONTENT_MISSING: " + android.text.TextUtils.join(", ", missing);
        } catch (RuntimeException | OutOfMemoryError error) {
            return "VALIDATION_ERROR: " + error.getMessage();
        } finally {
            if (readable != null && readable != sample.bitmap) {
                readable.recycle();
            }
        }
    }

    private static boolean similar(int a, int b) {
        return Math.abs(Color.red(a) - Color.red(b)) <= 8
                && Math.abs(Color.green(a) - Color.green(b)) <= 8
                && Math.abs(Color.blue(a) - Color.blue(b)) <= 8
                && Color.alpha(a) >= 247;
    }
}
