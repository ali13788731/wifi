# OkHttp / Okio
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Room entities
-keep class com.example.netspeed.core.database.** { *; }
# Cafe Bazaar billing (Poolakey)
-keep class ir.cafebazaar.poolakey.** { *; }
-dontwarn ir.cafebazaar.poolakey.**

# Smaller output
-allowaccessmodification
-repackageclasses ''
