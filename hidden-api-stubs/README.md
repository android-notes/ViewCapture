# Compile-only framework stubs

Compile-time declarations for Android 8–9 RenderNode and ThreadedRenderer, and Android 10+ HardwareRenderer. The HardwareRenderer stub includes the public members required by the library.

The `lib` module uses `compileOnly` and places these declarations before `android.jar` on its compile classpath. The stubs are excluded from the APK and AAR; runtime calls use the device framework.
