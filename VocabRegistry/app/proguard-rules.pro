# ============================================================
# Lexicon — R8/ProGuard rules
# The app's data layer round-trips JSON via kotlinx-serialization
# (vocab.json, writing.json, backups) and stores state in Room.
# These keeps prevent the shrinker from breaking that.
# ============================================================

# --- kotlinx-serialization (standard rules) ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.tahsin.vocabregistry.**$$serializer { *; }
-keepclassmembers class com.tahsin.vocabregistry.** {
    *** Companion;
}
-keepclasseswithmembers class com.tahsin.vocabregistry.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Room entities / database ---
-keep class com.tahsin.vocabregistry.data.model.** { *; }
-keep class * extends androidx.room.RoomDatabase

# --- Enums restored by name (Axis.valueOf, AxisStatus.valueOf, ThemeMode.valueOf) ---
-keepclassmembers enum com.tahsin.vocabregistry.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Widget provider is referenced from the manifest ---
-keep class com.tahsin.vocabregistry.widget.LexiconWidget { *; }

# (Play Billing, Mobile Ads, Compose, Room and WorkManager all ship their own
#  consumer rules — no extra keeps needed here.)
