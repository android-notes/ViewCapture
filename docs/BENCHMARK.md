# Release capture benchmark

The benchmark uses the demo's three capture methods: Software Canvas, ViewCapture, and PixelCopy stitching. It calls the same capture backends as the normal demo.

## Procedure

The target is the complete transparent LinearLayout containing 11 groups of mixed content, including looping TextureView video, gradients, hardware bitmaps, RenderEffect, complex drawing, and WebView. Video and web content remain dynamic. The target stays at its original dimensions, with scale 1.

Each method receives one warmup, followed by five measured single screenshots. Method order rotates each round. Each screenshot creates its own rendering resources; ViewCapture reuses rendering resources between tiles within that screenshot. Captures are separated by 250ms, with no forced GC or active cooling.

The metric is wall-clock time from the capture call until the complete bitmap is CPU-readable. It includes allocation, View recording and drawing, rendering, readback, stitching, and cleanup inside the capture method. PixelCopy also includes viewport scrolling, rendered-frame waits, and scroll-state restoration. Scene preparation, warmup, pixel validation, final bitmap recycling, file writes, and delays are excluded.

Each result checks dimensions, all 22 START/END markers, and two padding alpha probes. ViewCapture and PixelCopy also check 11 hardware-bitmap color probes. Software Canvas cannot reproduce all hardware drawing, while PixelCopy includes ancestor backgrounds; these timing results do not imply equivalent visual output. Dynamic content is not compared pixel for pixel.

## Reproduce

Requires Android 8+ (API 26+), JDK 17, and a connected device. The benchmark activity is included only when `captureBenchmark` is enabled.

```sh
./gradlew -PcaptureBenchmark=true :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -W -n io.github.viewcapture.demo.release/io.github.viewcapture.demo.CaptureBenchmarkActivity --activity-clear-top --ei trials 5
# Wait for "Benchmark complete" on the device.
adb pull /sdcard/Android/data/io.github.viewcapture.demo.release/files/capture-benchmark.json
```

Build without `-PcaptureBenchmark=true` for the normal demo APK.

## Recorded results

Measured on October 20, 2026: Xiaomi 24069RA21C, Android 15 / API 35, Release with R8 and resource shrinking enabled, `debuggable=false`. The full target measured 572 × 115728px with 154 direct child Views. Each method produced five measured samples after its warmup.

| Method | Mean, ms | Range, ms |
| --- | ---: | ---: |
| Software Canvas | 1511.71 | 1501.23–1533.62 |
| ViewCapture | **904.06** | 827.85–940.40 |
| PixelCopy stitching | 3481.09 | 3285.15–3577.72 |

All 18 captures (three warmups and 15 measured results) passed the dimension and applicable pixel probes. ViewCapture and Software Canvas preserved transparent padding; PixelCopy included the opaque ancestor background. Hardware-bitmap patches were verified for ViewCapture and PixelCopy. Full-image equality was not tested.

[Raw measurements](benchmarks/capture-2026-10-20.json) include every sample, warmup markers, device fingerprint, validity probes, and summary statistics. Results cover one device and one scene; they are not a guarantee of performance on other devices or content.
