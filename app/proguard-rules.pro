# CallAgent ProGuard Rules

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep our data classes (used with JSON serialization)
-keep class com.callagent.data.** { *; }
-keep class com.callagent.ai.Message { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Keep coroutines
-keepnames class kotlinx.coroutines.** { *; }
