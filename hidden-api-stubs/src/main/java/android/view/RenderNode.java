package android.view;

/** Compile-only Android 8–9 signatures. */
public final class RenderNode {
    public static RenderNode create(String name, View owner) {
        return null;
    }

    public boolean setLeftTopRightBottom(int left, int top, int right, int bottom) {
        return false;
    }

    public DisplayListCanvas start(int width, int height) {
        return null;
    }

    public void end(DisplayListCanvas canvas) {}

    public void discardDisplayList() {}
}
