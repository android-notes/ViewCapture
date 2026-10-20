# Preserve HiddenApiBypass entry points.
-keep class org.lsposed.hiddenapibypass.** { *; }
# Preserve lazy hidden API initialization.
-keep,allowobfuscation class com.wanjian.capture.ViewCapture$HiddenApiAccess { *; }
# Preserve API-specific class boundaries.
-keep,allowobfuscation class com.wanjian.capture.Api*CaptureNode { *; }
-keep,allowobfuscation class com.wanjian.capture.ImageReaderCaptureNode { *; }
# Hidden Android 8–9 framework types.
-dontwarn android.view.DisplayListCanvas
-dontwarn android.view.RenderNode
-dontwarn android.view.ThreadedRenderer
