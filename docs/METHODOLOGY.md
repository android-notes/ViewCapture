# How ViewCapture works

## View-level rendering

`ViewCapture.capture(view)` draws the target View's full local bounds into an offscreen rendering target. The original View stays in place, and transparent pixels remain transparent.

On Android 8+, the library records `View.draw()` with a hardware Canvas backed by a RenderNode. When the height fits the Canvas limit, `createHardwareBitmap()` produces the result directly. Android 8–9 uses the legacy framework interfaces; Android 10+ uses `android.graphics.RenderNode` and `HardwareRenderer`.

Android 5–7 uses a software Canvas through the same public capture API. CaptureNode implementations isolate the framework types required by each Android version.

`ViewCapture.capture(view, scale)` applies a uniform Canvas scale before drawing, with `0 < scale <= 1`. Output dimensions are rounded to at least one pixel. Rendering targets use these scaled dimensions; no full-resolution intermediate bitmap is created. The original overload uses scale `1`.

## Tall Views

The library compares the scaled output height with the RecordingCanvas maximum bitmap height and selects a single render or tiled capture automatically. Tiled capture proceeds as follows:

1. Clip a tile-sized Canvas in output pixels, translate by the tile's output offset, and apply the requested scale.
2. Record and render that section with hardware drawing.
3. Read back the tile and draw its pixels into the full-size output bitmap.
4. Release the intermediate bitmaps and continue with the next section.

Android 10+ reuses one ImageReader, HardwareRenderer, and RenderNode across tiles within a capture. It acquires the completed Image before proceeding. Android 8–9 reuses a legacy RenderNode and creates each hardware tile separately.

The final tiled result is a complete `ARGB_8888` bitmap. Its width and memory requirements remain subject to device limits.

## Bitmap ownership

The caller owns the returned bitmap and releases it after use. A single hardware capture returns `HARDWARE`; tiled captures and the software fallback return `ARGB_8888`. Copy a hardware bitmap to `ARGB_8888` when CPU pixel access is needed.

Temporary rendering resources are released when capture finishes or fails.

Renderer output is marked premultiplied before returning it or copying a tile. Android 8.1's [ThreadedRenderer](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.1.0_r1/core/jni/android_view_ThreadedRenderer.cpp) omits the premultiplication request when constructing its Java Bitmap. [Bitmap.copy()](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.1.0_r1/graphics/java/android/graphics/Bitmap.java) inherits that request, so an uncorrected software copy can be rejected by Canvas. The renderer's pixels are already premultiplied; this corrects their metadata without changing pixel values or removing transparency.

## Capture boundaries

Call on the main thread after the target is attached, displayed, and laid out. TextureView and video producers must have supplied a frame. Dynamic content can change between tiles.

The result covers the target's View hierarchy. SurfaceView buffers belong to separate surfaces and require a separate capture and composition step.

## Demo and validation

The demo displays ViewCapture output beside the original View with synchronized scrolling. It includes hardware drawing samples, transparent padding, and repeated content for tall captures. Software Canvas and PixelCopy serve as reference options for inspecting rendering differences.

The README uses a compact live-video, rounded-gradient, and RenderEffect scene. Launch it with the `showcase` intent extra; RenderEffect requires Android 12+ (API 31+).

```sh
adb shell am start -n io.github.viewcapture.demo.release/io.github.viewcapture.demo.MainActivity --activity-clear-top --ez showcase true
```

`ReadmeCaptureTest` exports the three original bitmap outputs and verifies that video frames change during capture. The [capture record](images/capture-info.json) includes the device, dimensions, and alpha probes.

Device tests check content markers, hardware bitmap pixels, tile boundaries, transparency, failure recovery, and bitmap ownership. See [validation coverage](VALIDATION.md).
