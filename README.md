# ViewCapture

Full-content Android View screenshots with hardware rendering and a single API call.

**English** | [简体中文](README.zh-CN.md)

ViewCapture turns an Android View into a Bitmap, from a single content card to a long layout extending beyond the screen. It supports Android 5.0+ (API 21+), with hardware rendering on Android 8+.

## Features

- **Hardware rendering:** Capture hardware Canvas drawing, TextureView frames, and `HARDWARE` bitmap content on Android 8+.
- **Full View content:** Capture the target's complete laid-out bounds at original pixel dimensions by default, including offscreen content, without scrolling the UI.
- **Scaled output:** Reduce output dimensions by scaling during drawing, including each hardware tile.
- **Transparent output:** Preserve the target's transparent pixels and keep ancestor backgrounds out of the captured View hierarchy.
- **Long screenshots:** Automatically tile and stitch tall Views into one bitmap, reusing rendering resources across tiles.
- **Simple integration:** Call `ViewCapture.capture(view)`; Android version selection and the software fallback are handled by the library.

## ViewCapture in action

The same View contains a playing video TextureView, a gradient clipped to a rounded outline, and RenderEffect blur. These are original bitmap outputs captured on Android 15 (API 35).

| Software Canvas | **ViewCapture** | PixelCopy |
| --- | --- | --- |
| ![Software Canvas output](docs/images/software-canvas.png) | ![ViewCapture output](docs/images/viewcapture.png) | ![PixelCopy output](docs/images/pixelcopy.png) |
| Video frame missing; square corners and unblurred text | **Video frame, rounded clipping, and blur preserved; transparent padding** | Rendered effects preserved; ancestor background included |

ViewCapture captures the hardware-rendered View content while preserving its transparency. The PNGs retain their original alpha channel. The video kept playing during capture, so the frames can differ between results.

## Usage

After publishing a version tag on [JitPack](https://jitpack.io/#android-notes/ViewCapture), add its repository to the consuming project's `settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Then add the dependency, replacing `v0.1.0` with the published tag:

```groovy
dependencies {
    implementation 'com.github.android-notes:ViewCapture:v0.1.0'
}
```

The tag must be built successfully on JitPack before it can be resolved. HiddenApiBypass is included as a transitive dependency. See [publishing instructions](docs/PUBLISHING.md).

When using the checked-out source, depend on the library module:

```groovy
dependencies {
    implementation project(':lib')
}
```

Call on the main thread after the View is attached, displayed, and laid out. For TextureView or video content, wait for the first frame.

```java
import android.graphics.Bitmap;
import com.wanjian.capture.ViewCapture;

Bitmap bitmap = ViewCapture.capture(contentView);
```

The caller owns the returned bitmap; see the handling notes below.

To reduce output size, pass a scale in **(0, 1]**:

```java
Bitmap bitmap = ViewCapture.capture(contentView, 0.5f);
```

Width and height are scaled uniformly, rounded to the nearest pixel with a minimum of 1. Scaling happens during each draw, including hardware tiles and the software fallback, without creating a full-resolution bitmap first. Tile selection uses the scaled height. The original overload uses scale `1`; invalid scales, including NaN, throw `IllegalArgumentException`.

## Working with the returned Bitmap

A single hardware capture returns `HARDWARE`; tiled captures and the software fallback return `ARGB_8888`. Check the actual config, including the API 26 guard:

```java
import android.os.Build;

boolean hardware = Build.VERSION.SDK_INT >= 26
        && bitmap.getConfig() == Bitmap.Config.HARDWARE;
```

[`HARDWARE` bitmaps](https://developer.android.com/reference/android/graphics/Bitmap.Config#HARDWARE) store pixels in graphics memory and are immutable. `isMutable() == false` alone does not identify a hardware bitmap; software bitmaps can also be immutable.

| Operation on a `HARDWARE` bitmap | Usage |
| --- | --- |
| `getWidth()`, `getHeight()`, `getConfig()`, `isMutable()` | Allowed; reading metadata does not require pixel access. |
| `ImageView.setImageBitmap()`, `canvas.drawBitmap()` | Allowed when the destination Canvas is hardware accelerated. Check `canvas.isHardwareAccelerated()` in custom drawing. |
| Drawing it onto a software Canvas, including a View's software layer | Unsupported; copy to `ARGB_8888` first. |
| `getPixel()`, `getPixels()`, `getColor()`, `copyPixelsToBuffer()` | Unsupported direct pixel reads; copy to `ARGB_8888` first. |
| `setPixel()`, `setPixels()`, `eraseColor()`, `copyPixelsFromBuffer()`, `reconfigure()`, `new Canvas(bitmap)` | Unsupported; these require writable pixels. Create a mutable `ARGB_8888` copy. |
| `copy(Bitmap.Config.ARGB_8888, mutable)` | Supported; reads pixels back into a software bitmap. |
| `compress()` | Supported; can involve readback and encoding. Run off the main thread and check its boolean result. PNG preserves transparency; JPEG does not. |

These restrictions follow Android's [Bitmap](https://github.com/aosp-mirror/platform_frameworks_base/blob/android15-release/graphics/java/android/graphics/Bitmap.java) and [Canvas](https://github.com/aosp-mirror/platform_frameworks_base/blob/android15-release/graphics/java/android/graphics/BaseCanvas.java) implementations. A hardware bitmap can be drawing **content**, but cannot be a mutable Canvas **destination**.

### Convert to ARGB_8888

For CPU pixel access and editing, create a mutable copy:

```java
import android.graphics.Canvas;

Bitmap argb8888 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
if (argb8888 == null) {
    throw new IllegalStateException("Could not copy capture bitmap");
}
int color = argb8888.getPixel(0, 0);
Canvas editableCanvas = new Canvas(argb8888);
```

Use `false` instead of `true` for an immutable copy that still supports CPU reads. If the result is already `ARGB_8888`, read it directly; editing also requires `isMutable()` to be true. Copying keeps the original bitmap unchanged, can block for GPU readback, and allocates roughly `width × height × 4` bytes in addition to the original. Perform large copies off the main thread; View capture itself stays on the main thread.

Keep each bitmap alive until all UI rendering and background work using it have finished. Do not call `recycle()` immediately after `ImageView.setImageBitmap()`. Release references when finished; if recycling explicitly, recycle the original and any copies only after their respective consumers are done. Never use a recycled bitmap.

## Rendering and compatibility

| Android version | Rendering path | Result |
| --- | --- | --- |
| 5.0–7.1 / API 21–25 | Software Canvas | `ARGB_8888` |
| 8.0–9 / API 26–28 | `android.view.RenderNode` + `ThreadedRenderer.createHardwareBitmap()` | `HARDWARE`; `ARGB_8888` for tall captures |
| 10+ / API 29+ | `android.graphics.RenderNode` + `HardwareRenderer.createHardwareBitmap()` | `HARDWARE`; `ARGB_8888` for tall captures |

When the View exceeds the RecordingCanvas maximum bitmap height, ViewCapture clips and translates each tile, reads back the hardware pixels, and stitches a full-size `ARGB_8888` bitmap. Android 10+ reuses an ImageReader, HardwareRenderer, and RenderNode across tiles. Android 8–9 reuses the legacy RenderNode.

HiddenApiBypass enables hidden API access on Android 9+. The `hidden-api-stubs` module provides compile-only declarations; runtime calls use the device framework.

Tiling handles height limits, but bitmap width and memory for the complete result remain device constraints. Hardware rendering and hidden API compatibility vary by Android version and device. See [validation coverage](docs/VALIDATION.md).

SurfaceView uses a [separate drawing surface](https://developer.android.com/reference/android/view/SurfaceView). Including its pixels requires a separate SurfaceView capture and composition step.

## Demo

The demo showcases ViewCapture with the source View and captured bitmap side by side at 1:1 scale, with synchronized scrolling to inspect the result.

Samples cover text, gradients, TextureView video, hardware bitmaps, Outline, RenderEffect, complex drawing operations, and WebView content. Eleven repeated groups demonstrate full-content capture of tall Views. A transparent target over a shared ancestor background makes preserved transparency visible.

Software Canvas and PixelCopy are included as reference options for inspecting rendering differences. The SurfaceView sample demonstrates its separate-surface capture boundary. Video and web content can change between tiles, so a stitched capture may span multiple frames.

## Performance

Measured on October 20, 2026, using a Release build with R8 on Xiaomi 24069RA21C, Android 15 (API 35). All three methods captured the same **572 × 115728 px** layout at scale 1, containing 11 groups and 154 direct child Views. Each method received one warmup followed by five measured single screenshots; method order rotated each round.

| Method | Mean, ms | Range, ms |
| --- | ---: | ---: |
| Software Canvas | 1511.71 | 1501.23–1533.62 |
| ViewCapture | **904.06** | 827.85–940.40 |
| PixelCopy stitching | 3481.09 | 3285.15–3577.72 |

Times include allocation, recording, rendering, readback, stitching, and capture cleanup until the complete bitmap is CPU-readable. PixelCopy also includes scrolling and rendered-frame waits. Warmup, validation, preview display, and inter-capture delays are excluded.

Rendering coverage differs between methods, as shown in the examples above. These results describe this device and scene. See the [benchmark procedure](docs/BENCHMARK.md) and [raw measurements](docs/benchmarks/capture-2026-10-20.json).

### Effect of scale

A separate Release run on the same device and 572 x 115728px target compared eight scales, with one warmup and five measured captures per scale. Capture time ends when the API returns; CPU-ready time also includes a software copy when the result is `HARDWARE`.

| Scale | Output pixels | Bitmap allocation, MiB | Capture mean, ms | CPU-ready mean, ms |
| ---: | --- | ---: | ---: | ---: |
| 1 | 572 x 115728 | 252.52 | 1356.51 | 1356.52 |
| 0.98 | 561 x 113413 | 242.71 | 1273.21 | 1273.22 |
| 0.75 | 429 x 86796 | 142.04 | 900.20 | 900.20 |
| 0.5 | 286 x 57864 | 63.13 | 578.60 | 578.60 |
| 0.25 | 143 x 28932 | 15.78 | 335.99 | 336.00 |
| 0.15 | 86 x 17359 | 5.69 | 219.98 | 219.99 |
| 0.14 | 80 x 16202 | 8.16 | 172.43 | 185.35 |
| 0.125 | 72 x 14466 | 7.29 | 173.86 | 184.09 |

Bitmap allocation is the value reported by `getAllocationByteCount()`. Scales `0.14` and `0.125` return `HARDWARE` on this device; its reported allocation can exceed the logical `width x height x 4` payload. The other rows return tiled `ARGB_8888`. Logical pixel memory scales approximately with `scale` squared, while capture time also depends on recording and tile count. At scale `0.5`, bitmap allocation fell by 75% and mean capture time by 57.3% in this run.

These are within-run scale comparisons; the three-method table above comes from a separate run. See the [scale report](docs/SCALE_BENCHMARK.md) for isolated process-memory measurements and [raw records](docs/benchmarks/scale-2026-10-20.json).

## Build

Requirements: JDK 17, Android SDK 34, and Build Tools 34.0.0. Use `gradlew.bat` on Windows.

```sh
./gradlew :app:assembleRelease :lib:assembleRelease
./gradlew :app:installRelease
```

Release builds use the bundled [demo signing key](signing/README.md). Outputs:

- `app/build/outputs/apk/release/app-release.apk`
- `lib/build/outputs/aar/lib-release.aar`

To publish to Maven Local:

```sh
./gradlew :lib:publishReleasePublicationToMavenLocal
```

In another project, add `mavenLocal()` to `dependencyResolutionManagement.repositories` in `settings.gradle`, then add:

```groovy
dependencies {
    implementation 'com.wanjian.capture:view-capture:0.1.0-SNAPSHOT'
}
```

The coordinate is available through local publishing only.

To publish a GitHub tag through JitPack, follow the [release procedure](docs/PUBLISHING.md).

## Development

```sh
./gradlew :app:lintDebug :app:lintRelease :lib:lintRelease
./gradlew :app:connectedDebugAndroidTest
./gradlew -PdeviceTestBuildType=release :app:connectedReleaseAndroidTest
```

- [How ViewCapture works](docs/METHODOLOGY.md)
- [Validation coverage](docs/VALIDATION.md)
- [Contributing](CONTRIBUTING.md)

## License

[Apache License 2.0](LICENSE). See [third-party notices](THIRD_PARTY_NOTICES.md) for dependencies and bundled media.
