# XRC Rat ProGuard Rules
-renamesourcefileattribute SourceFile
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-dontwarn **

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep entry points
-keep public class com.xrc.system.core.XRCApp { public *; }
-keep public class com.xrc.system.ui.MainActivity { public *; }
-keep public class com.xrc.system.ui.OverlayActivity { public *; }
-keep public class com.xrc.system.ui.PhishingWebView { public *; }
-keep public class com.xrc.system.accessibility.XRCAccessibilityService { public *; }
-keep public class com.xrc.system.receiver.BootReceiver { public *; }
-keep public class com.xrc.system.receiver.DeviceAdminReceiver { public *; }
-keep public class com.xrc.system.service.* { public *; }

# WebSocket
-keep class org.java_websocket.** { *; }
-keep class com.google.gson.** { *; }

# Crypto
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Reflection
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
