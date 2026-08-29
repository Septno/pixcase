# Compose 规则
-keep class androidx.compose.runtime.** { *; }

# Hilt / Dagger 规则
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp

# Room 规则
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Coil 规则
-dontwarn coil3.**

# ML Kit
-keep class com.google.mlkit.** { *; }

# 保留 Kotlin 协程内部
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# 保留行号供崩溃日志
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 阶段 0 调试用,生产前再加固
-dontoptimize