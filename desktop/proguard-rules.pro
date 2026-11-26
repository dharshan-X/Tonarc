# Optional platform/native-image/Android classes referenced by OkHttp, Retrofit, and Jsoup
-dontwarn org.graalvm.nativeimage.**
-dontwarn com.oracle.svm.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn com.google.re2j.**
-dontwarn android.**
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.invoke.**

# Keep Tonarc models, network interfaces, and serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.quietrays.tonarc.desktop.model.** { *; }
-keep class com.quietrays.tonarc.data.model.** { *; }
-keep class com.quietrays.tonarc.data.network.lyrics.** { *; }
-keep class com.quietrays.tonarc.data.network.youtube.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}
-keep,includedescriptorclasses class com.quietrays.tonarc.**$$serializer { *; }

# Retrofit & Gson
-keepattributes Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# NewPipeExtractor & Rhino JS engine
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# JAudioTagger & VorbisJava
-keep class org.jaudiotagger.** { *; }
-keep class org.gagravarr.** { *; }
-dontwarn org.jaudiotagger.**

# Coil 3
-keep class coil3.** { *; }
-dontwarn coil3.**
