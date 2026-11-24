# Zero Play ProGuard / R8 Keep Rules

# NewPipeExtractor & Mozilla Rhino JavaScript Engine
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-keep class org.schabi.newpipe.** { *; }
-dontwarn org.schabi.newpipe.**

# FFmpegKit (full-gpl) native JNI bindings
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# yt-dlp-android / youtubedl-android native bindings
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

# AndroidX Media3 AV1 (dav1d / gav1) & NextLib FFmpeg extension renderers
-keep class androidx.media3.decoder.av1.** { *; }
-dontwarn androidx.media3.decoder.av1.**
-keep class io.github.anilbeesetti.nextlib.** { *; }
-dontwarn io.github.anilbeesetti.nextlib.**
