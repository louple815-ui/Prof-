# PROF+ R8 / ProGuard Configuration (ONADJA PRODUCTION)

-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Preserve PROF+ Firestore / Room domain models and @Keep / @IgnoreExtraProperties annotations
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers @androidx.annotation.Keep class * { *; }
-keep @com.google.firebase.firestore.IgnoreExtraProperties class * { *; }
-keepclassmembers class com.example.data.model.** {
    public <init>(...);
    *;
}
-keepclassmembers class com.example.data.local.** {
    public <init>(...);
    *;
}

# Firebase Firestore, Auth, Storage, AI Logic, and App Check
-keepattributes Exceptions
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.firebase.ai.** { *; }
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.storage.** { *; }
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.appcheck.** { *; }

# Google Play Services, Credential Manager, and Google ID
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.gms.**

# Kotlinx Serialization / Coroutines / Ktor (used by Firebase AI Logic SDK)
-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer();
}
-dontwarn kotlinx.serialization.**
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# Room & Jetpack Compose runtime preservation
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.**
