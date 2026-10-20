package android.graphics;

/** Compile-only signature. Runtime calls resolve to the Android framework implementation. */
@android.annotation.TargetApi(29)
public final class HardwareRenderer {
    public static final int SYNC_LOST_SURFACE_REWARD_IF_FOUND = 2;
    public static final int SYNC_CONTEXT_IS_STOPPED = 4;
    public static final int SYNC_FRAME_DROPPED = 8;

    public static Bitmap createHardwareBitmap(RenderNode node, int width, int height) {
        return null;
    }

    public void setOpaque(boolean opaque) {}

    public void setSurface(android.view.Surface surface) {}

    public void setContentRoot(RenderNode node) {}

    public FrameRenderRequest createRenderRequest() {
        return null;
    }

    public void destroy() {}

    public static final class FrameRenderRequest {
        public FrameRenderRequest setWaitForPresent(boolean wait) {
            return this;
        }

        public int syncAndDraw() {
            return 0;
        }
    }
}
