# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Google ML Kit classes
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep accessibility service
-keep class com.translateassist.service.TranslateAccessibilityService { *; }
-keep class com.translateassist.service.TranslateAccessibilityService { *; }
# Keep all service package classes (defensive against future refactors)
-keep class com.translateassist.service.** { *; }
# Keep Application subclass
-keep class com.translateassist.App { *; }


# Strip out Log calls in release (they become no-ops)
-assumenosideeffects class android.util.Log {
	public static int v(...);
	public static int d(...);
	public static int i(...);
	public static int w(...);
	public static int e(...);
	public static int wtf(...);
}

# (Optional) Strip Toast debug variants if you wrap them later (currently not wrapped)