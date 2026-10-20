# ViewCapture scale benchmark

The benchmark calls `ViewCapture.capture(view, scale)` on the complete 11-group mixed scene, including dynamic TextureView video and WebView. The target remains laid out at its original size; scaling happens during capture drawing.

## Timing

Each scale receives one warmup and five measured single captures. Scale order rotates each round, with 250ms between captures and no forced GC. No memory sampler runs during these captures.

- **Capture:** Wall-clock time until `ViewCapture.capture()` returns, including recording, rendering, readback and stitching performed by the library, and its resource cleanup.
- **CPU-ready:** Capture time plus an explicit `copy(ARGB_8888, false)` when the returned bitmap is `HARDWARE`. Tiled output is already CPU-readable.

Validation, final bitmap recycling, file writes, UI preview, and delays are excluded. Every result checks its scaled dimensions, all 22 START/END markers, 11 hardware-bitmap color probes, and two transparent-padding probes. Dynamic content is not checked pixel for pixel.

## Memory

Memory captures run separately from timing. Each scale starts in a fresh app process, loads the same scene, waits for producer readiness and five additional seconds, then captures once without a capture warmup or forced GC.

- **Bitmap payload:** `width × height × 4` bytes. `getAllocationByteCount()` is also recorded. For `HARDWARE`, this is not a measurement of the driver's complete GPU allocation or row alignment.
- **Sampled peak RSS increase:** Maximum `/proc/self/status` VmRSS observed during capture minus the pre-capture baseline. The sampler targets 10ms intervals and also samples at capture completion. This is an approximate observed peak, not an exact allocation counter; short peaks can be missed. Kernel RSS accounting is also asynchronous. See the [Linux proc documentation](https://docs.kernel.org/filesystems/proc.html#process-specific-subdirectories).
- **Retained Graphics PSS change:** Android's `summary.graphics` immediately after capture returns, while the result is still held, minus its pre-capture value. It is not a sampled GPU peak and should not be added to RSS. See [Android memory accounting](https://developer.android.com/topic/performance/memory-overview).

Memory sampling stops before optional CPU readback and validation, so the extra software copy of a `HARDWARE` result is excluded. Raw records also contain before/retained RSS, total PSS, Java heap use, and native allocated bytes. Measurements cover the app's main process; separate WebView renderer processes are excluded. Video, web loading, allocators, and driver caches can affect the process deltas, so the single memory capture per scale is diagnostic rather than a precise isolated allocation measurement.

## Reproduce

Requires Android 10+ (API 29+) for this benchmark activity. Build and install the optional Release benchmark:

```sh
./gradlew -PcaptureBenchmark=true :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am force-stop io.github.viewcapture.demo.release
adb shell am start -W -n io.github.viewcapture.demo.release/io.github.viewcapture.demo.ScaleBenchmarkActivity
# Wait for "Scale benchmark complete".
adb pull /sdcard/Android/data/io.github.viewcapture.demo.release/files/scale-timing.json
```

For each isolated memory capture, force-stop before launching with the desired scale:

```sh
adb shell am force-stop io.github.viewcapture.demo.release
adb shell am start -W -n io.github.viewcapture.demo.release/io.github.viewcapture.demo.ScaleBenchmarkActivity --ez memoryOnly true --ef scale 0.5
# Wait for "Scale benchmark complete".
adb pull /sdcard/Android/data/io.github.viewcapture.demo.release/files/scale-memory-0.5.json
```

Repeat for `1.0`, `0.98`, `0.75`, `0.5`, `0.25`, `0.15`, `0.14`, and `0.125`. Build without `-PcaptureBenchmark=true` for the normal demo APK.

## Recorded results

October 20, 2026; Xiaomi 24069RA21C; Android 15 / API 35; Release with R8 and resource shrinking, `debuggable=false`. The target measured 572 x 115728px and the RecordingCanvas maximum bitmap height was 16384px. All 48 timing captures and eight isolated memory captures passed the dimension and pixel probes. Each memory capture used a different process ID.

| Scale | Output pixels | Tiles | Config | Capture mean, ms | CPU-ready mean, ms | Capture range, ms |
| ---: | --- | ---: | --- | ---: | ---: | --- |
| 1 | 572 x 115728 | 8 | ARGB_8888 | 1356.51 | 1356.52 | 1339.42-1370.65 |
| 0.98 | 561 x 113413 | 7 | ARGB_8888 | 1273.21 | 1273.22 | 1193.78-1351.98 |
| 0.75 | 429 x 86796 | 6 | ARGB_8888 | 900.20 | 900.20 | 842.35-958.12 |
| 0.5 | 286 x 57864 | 4 | ARGB_8888 | 578.60 | 578.60 | 570.65-597.06 |
| 0.25 | 143 x 28932 | 2 | ARGB_8888 | 335.99 | 336.00 | 300.38-352.55 |
| 0.15 | 86 x 17359 | 2 | ARGB_8888 | 219.98 | 219.99 | 185.05-247.56 |
| 0.14 | 80 x 16202 | 1 | HARDWARE | 172.43 | 185.35 | 158.43-185.00 |
| 0.125 | 72 x 14466 | 1 | HARDWARE | 173.86 | 184.09 | 153.68-199.61 |

| Scale | Logical pixels, MiB | Reported bitmap allocation, MiB | Sampled peak RSS increase, MiB | Retained Graphics PSS change, MiB |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 252.52 | 252.52 | 358.48 | +4.25 |
| 0.98 | 242.71 | 242.71 | 323.46 | +4.68 |
| 0.75 | 142.04 | 142.04 | 228.72 | +2.62 |
| 0.5 | 63.13 | 63.13 | 122.00 | +1.25 |
| 0.25 | 15.78 | 15.78 | 60.07 | -0.10 |
| 0.15 | 5.69 | 5.69 | 45.48 | +0.02 |
| 0.14 | 4.94 | 8.16 | 23.54 | -0.29 |
| 0.125 | 3.97 | 7.29 | 26.09 | -0.26 |

The Graphics PSS changes near zero, including negative values, show that this process statistic is affected by other scene resources and cache changes. They do not represent negative bitmap allocations or establish the GPU footprint of the result. RSS deltas include temporary capture resources and background scene activity, and exclude the optional CPU copy of a hardware result.

The logical bitmap payload at scale 0.5 is one quarter of scale 1; reported bitmap allocation fell from 252.52 to 63.13 MiB. Average API-return time fell from 1356.51 to 578.60ms. Speed does not follow the pixel-area ratio because recording, traversal, and per-tile work remain.

At scale 0.15, output height was 17359px and required two tiles, returning ARGB_8888. At 0.14, output height was 16202px and fit one hardware capture. The return type and allocation accounting therefore changed. The 0.14 hardware result reported 8.16 MiB despite a 4.94 MiB logical payload; it must not be compared as if all backends used tightly packed software memory. Scales 0.14 and 0.125 had similar timings in this run; smaller pixels alone do not guarantee a proportional time reduction.

[Raw records](benchmarks/scale-2026-10-20.json) retain all timing samples, all eight cold-process memory records, process IDs, device fingerprint, and the benchmark APK SHA-256. The separate three-method benchmark was collected in a different run; compare ratios within this scale run rather than combining absolute timings between runs.
