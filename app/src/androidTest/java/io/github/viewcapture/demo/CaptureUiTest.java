package io.github.viewcapture.demo;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.FileInputStream;
import java.util.List;

/** Black-box UI test for debug and R8 release builds. */
@RunWith(AndroidJUnit4.class)
public final class CaptureUiTest {
    @Test
    public void methodButtonsUpdateSideBySideBitmapWithoutDialogs() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        android.accessibilityservice.AccessibilityServiceInfo service =
                instrumentation.getUiAutomation().getServiceInfo();
        service.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        instrumentation.getUiAutomation().setServiceInfo(service);
        String targetPackage = instrumentation.getTargetContext().getPackageName();
        boolean debug =
                (instrumentation.getTargetContext().getApplicationInfo().flags
                                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)
                        != 0;
        String expectedIndicator =
                debug ? "Install the Release build to compare capture times" : " ms · ";
        try (ParcelFileDescriptor descriptor =
                        instrumentation
                                .getUiAutomation()
                                .executeShellCommand(
                                        "am start -W -n "
                                                + targetPackage
                                                + "/io.github.viewcapture.demo.MainActivity");
                FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            byte[] buffer = new byte[1024];
            while (input.read(buffer) != -1) {}
        }
        // Repeat both software and hardware outputs to exercise replacement and readback cleanup.
        for (String name :
                new String[] {
                    "Software Canvas",
                    "ViewCapture",
                    "PixelCopy stitching",
                    "Software Canvas",
                    "ViewCapture"
                }) {
            AccessibilityNodeInfo button = waitForText(instrumentation, name, 8000);
            assertNotNull("Method button must be visible: " + name, button);
            if (name.equals("PixelCopy stitching") && android.os.Build.VERSION.SDK_INT < 26) {
                assertFalse("Unsupported method must be disabled", button.isEnabled());
                continue;
            }
            assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK));
            long deadline = SystemClock.uptimeMillis() + 15000;
            while (SystemClock.uptimeMillis() < deadline) {
                AccessibilityNodeInfo status = findById(instrumentation, targetPackage, "state");
                AccessibilityNodeInfo preview =
                        findById(instrumentation, targetPackage, "bitmap_compare");
                button = waitForText(instrumentation, name, 1000);
                if (button != null
                        && button.isEnabled()
                        && status != null
                        && String.valueOf(status.getText()).startsWith(name + " · ")
                        && ((String.valueOf(status.getText()).contains(expectedIndicator)
                                        && preview != null
                                        && preview.isScrollable())
                                || String.valueOf(status.getText()).contains("Capture failed: ")
                                || String.valueOf(status.getText()).contains("Preview failed: "))) {
                    break;
                }
                SystemClock.sleep(50);
            }
            String statusText =
                    String.valueOf(findById(instrumentation, targetPackage, "state").getText());
            System.out.println(statusText);
            assertTrue(
                    name + " must finish successfully: " + statusText,
                    statusText.startsWith(name + " · ") && statusText.contains(expectedIndicator));
            if (debug) {
                assertFalse(statusText, statusText.contains(" ms"));
            }
            assertNotNull(
                    waitForText(
                            instrumentation,
                            "Captured bitmap, original pixels, synchronized scrolling",
                            1000));
            AccessibilityNodeInfo left = findById(instrumentation, targetPackage, "scene_scroll");
            AccessibilityNodeInfo right =
                    findById(instrumentation, targetPackage, "bitmap_compare");
            android.graphics.Rect a = new android.graphics.Rect(), b = new android.graphics.Rect();
            left.getBoundsInScreen(a);
            right.getBoundsInScreen(b);
            assertTrue("Bitmap must be beside the source", a.right <= b.left);
            assertEquals(a.top, b.top);
            assertEquals(a.bottom, b.bottom);
            assertTrue(left.isScrollable() && right.isScrollable());
            assertTrue(
                    "Comparison must not open a close-button dialog",
                    instrumentation
                            .getUiAutomation()
                            .getRootInActiveWindow()
                            .findAccessibilityNodeInfosByText("Close")
                            .isEmpty());
            assertNoWarnings(instrumentation.getUiAutomation().getRootInActiveWindow());
            assertNull(findById(instrumentation, targetPackage, "scenes"));
            assertNull(findById(instrumentation, targetPackage, "export"));
            assertNull(findById(instrumentation, targetPackage, "capture_reader"));
            if (name.equals("PixelCopy stitching")) {
                saveScreenshot(instrumentation, "pixel-copy-background.png");
            }
            if (name.equals("ViewCapture")) {
                List<AccessibilityNodeInfo> video =
                        instrumentation
                                .getUiAutomation()
                                .getRootInActiveWindow()
                                .findAccessibilityNodeInfosByText("TextureView · Video");
                if (!video.isEmpty() && video.get(0).isVisibleToUser()) {
                    saveScreenshot(instrumentation, "video-comparison.png");
                }
                assertTrue(
                        findById(instrumentation, targetPackage, "scene_scroll")
                                .performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD));
            }
        }
        assertNoWarnings(instrumentation.getUiAutomation().getRootInActiveWindow());
        // Wait for accessibility scrolling to settle before taking the screenshot.
        SystemClock.sleep(500);
        saveScreenshot(instrumentation, "comparison-smoke.png");
        for (int i = 0; i < 4; i++) {
            if (!findById(instrumentation, targetPackage, "scene_scroll")
                    .performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                break;
            }
            SystemClock.sleep(250);
            saveScreenshot(instrumentation, "complex-drawing-" + i + ".png");
        }
    }

    private void saveScreenshot(Instrumentation instrumentation, String name) throws Exception {
        android.graphics.Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (java.io.FileOutputStream output =
                new java.io.FileOutputStream(
                        new java.io.File(
                                instrumentation.getTargetContext().getExternalFilesDir(null),
                                name))) {
            assertTrue(
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            screenshot.recycle();
        }
    }

    private AccessibilityNodeInfo findById(
            Instrumentation instrumentation, String targetPackage, String id) {
        AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
        if (root == null) {
            return null;
        }
        List<AccessibilityNodeInfo> matches =
                root.findAccessibilityNodeInfosByViewId(targetPackage + ":id/" + id);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private void assertNoWarnings(AccessibilityNodeInfo node) {
        if (node == null) {
            return;
        }
        String text = String.valueOf(node.getText());
        assertFalse(
                text,
                text.contains("CONTENT_MISSING")
                        || text.contains("missing")
                        || text.contains("independent Surface"));
        for (int i = 0; i < node.getChildCount(); i++) assertNoWarnings(node.getChild(i));
    }

    private AccessibilityNodeInfo waitForText(
            Instrumentation instrumentation, String text, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(text);
                if (!matches.isEmpty()) {
                    return matches.get(0);
                }
            }
            SystemClock.sleep(50);
        }
        return null;
    }
}
