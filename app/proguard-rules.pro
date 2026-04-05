# ProGuard rules for XBee Drone App

# OSMDroid
-keep class org.osmdroid.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep network protocol classes
-keep class com.xbeedrone.app.network.** { *; }
-keep class com.xbeedrone.app.model.** { *; }

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
