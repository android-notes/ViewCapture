# ViewCapture validation

## Current status

The ViewCapture library and demo passed Debug and Release builds, instrumentation test APK compilation, and app/library Lint.

The scale overload passed three targeted Debug device tests on Android 15 on 2026-10-20: scaling is applied before View drawing, rounded dimensions stay at least one pixel, invalid ratios are rejected, nested hardware bitmap content survives, and scaled tile boundaries preserve every output row and transparent edge. The existing unscaled tile-boundary and draw-failure recovery test also passed. Scaled output heights of 16384, 16385, and 32805px exercised a single render, a one-row tail, and multiple tiles.

On 2026-10-20, five targeted Debug device checks passed on Android 15: live-video README capture, the three-button UI, full-height capture of 11 groups, transparent labels, and video playback with an immutable captured result. The README images are the original 572 × 1536px outputs; the [capture record](images/capture-info.json) confirms changing video frames and transparent padding for ViewCapture. The current Release build has not received a new device UI test.

## Recorded device results

Device: Xiaomi 24069RA21C, Android 15 / API 35, 1220 × 2712px.

Coverage included:

- Every row and transparent edge at hardware tile boundaries, including one-row and partial final tiles.
- Recovery after a drawing exception in a later tile.
- Full-height capture of 11 sample groups, with all 22 START/END markers and applicable hardware-bitmap color probes.
- TextureView frames, video updates, SurfaceView capture limitations, and transparent sample labels.
- PixelCopy strip continuity, cancellation, and scroll-state restoration.
- Repeated captures, preview replacement, pixel scale, and synchronized scrolling.
- Software CaptureNode pixels and bitmap ownership transfer.

The tall fixture measured 572 × 115728px with 154 direct child Views. The device's RecordingCanvas maximum bitmap height was 16384px, requiring eight hardware tiles. Pixel probes cover selected content and boundaries rather than the entire image.

## Performance benchmark

The [Release benchmark](BENCHMARK.md) compares Software Canvas, ViewCapture, and PixelCopy stitching, with recorded samples and a reproducible procedure.

On 2026-10-20, all 18 captures passed the dimension, content-marker, and applicable hardware-bitmap and padding-alpha probes. Each method received one warmup and five measured captures of the complete 572 × 115728px target.

The [scale benchmark](SCALE_BENCHMARK.md) also passed all 56 captures on the same device: 48 timing captures across eight scales and eight memory captures in separate processes. Both tiled ARGB_8888 output and single HARDWARE output were validated.

## Publishing checks

On 2026-10-20, local Maven publication and a local simulation of JitPack's environment both passed. Checks covered the AAR, sources, Javadoc, POM, Gradle module metadata, and consumer ProGuard rules. HiddenApiBypass was the runtime dependency; compile-only framework stubs were excluded. A standalone Android project resolved `com.github.android-notes:ViewCapture:v0.1.0` from the simulated Maven repository and compiled successfully with its transitive dependency. Remote JitPack publishing still requires a pushed tag and a successful JitPack build.

## Compatibility coverage

Android 5.0–9 has not been validated on a local device. CI is configured for API 21, 23, 25, 26, 27, 28, 29, and 35; consult its reports for results. Shadows, Outline, and RenderEffect require manual visual checks.
