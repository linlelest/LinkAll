# LinkALL Android R8 全开规则
# 通用 Android 选项
-dontwarn android.support.**
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

# Kotlin 协程
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Kotlinx Serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class com.linkall.android.**$$serializer { *; }
-keepclassmembers class com.linkall.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.linkall.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Koin
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# WebRTC（关键：保留原生库反射调用）
-keep class org.webrtc.** { *; }
-keep class org.webrtc.**$* { *; }
-dontwarn org.webrtc.**
-keepclassmembers class org.webrtc.** { *; }

# Compose
-dontwarn androidx.compose.**

# Markwon
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**

# Coil
-dontwarn coil.**

# 保留 Application / Service / Activity 入口
-keep class com.linkall.android.LinkALLApp { *; }
-keep class com.linkall.android.MainActivity { *; }
-keep class com.linkall.android.service.** { *; }
-keep class com.linkall.android.webrtc.** { *; }
