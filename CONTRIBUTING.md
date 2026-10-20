# Contributing

Contributions focus on ViewCapture's rendering quality, full-content capture, Android compatibility, and ease of integration.

When reporting a capture issue, include the Android version, device model, sample or View hierarchy, output dimensions, expected behavior, and relevant screenshots.

Before submitting changes, run:

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :lib:lintRelease
./gradlew :app:connectedDebugAndroidTest
./gradlew -PdeviceTestBuildType=release :app:connectedReleaseAndroidTest
```

Keep the library independent of the demo app, maintain Android 5.0+ compatibility, and release temporary rendering resources on success and failure. Update usage examples and relevant tests when changing the public API or capture behavior.

Use English for source comments, UI text, accessibility descriptions, and test messages. Explain non-obvious constraints and resource ownership; omit implementation history and details already clear from the code. Keep [README.md](README.md) and [README.zh-CN.md](README.zh-CN.md) aligned, and update UI test selectors when changing demo labels.

Use four-space indentation. Always add braces to `if` and `else` bodies, including single statements. Put XML attributes on separate lines; keep text-only resource entries on individual lines.

Validate captured content, transparency, and resource cleanup on representative Views and Android versions. See [how ViewCapture works](docs/METHODOLOGY.md) and the [validation coverage](docs/VALIDATION.md).
