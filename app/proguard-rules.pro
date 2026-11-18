# Keep protocol/model classes used through reflection or Gson.
-keep class com.sour.duoremote.data.api.** { *; }
-keep class com.sour.duoremote.data.session.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Tink/security-crypto references annotation-only artifacts that R8 cannot resolve.
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**

