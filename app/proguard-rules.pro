# Albion Radar ProGuard Rules

# Keep all data classes for JSON serialization
-keep class com.albionradar.data.** { *; }
-keep class com.albionradar.network.** { *; }

# Keep Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep native methods for VPN
-keepclasseswithmembernames class * {
    native <methods>;
}

# Optimize
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
