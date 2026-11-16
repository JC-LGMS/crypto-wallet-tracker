# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.jclg.cryptowallettracker.**$$serializer { *; }
-keepclassmembers class com.jclg.cryptowallettracker.** {
    *** Companion;
}

# WorkManager / Room (instanciation par réflexion)
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.utils.ForceStopRunnable { *; }
-keepnames class com.jclg.cryptowallettracker.sync.BalanceSyncWorker
-keep class com.jclg.cryptowallettracker.sync.BalanceSyncWorker { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# ZXing
-dontwarn com.google.zxing.**
