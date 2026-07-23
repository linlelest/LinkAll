# LinkALL Android R8 全开规则

# === 通用选项 ===
# 兜底：忽略所有缺失类警告（第三方库引用运行时类，编译期无法解析）
-dontwarn android.support.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
# Google Error Prone 注解（仅编译期静态分析用，Tink/security-crypto 引用但运行时无需）
-dontwarn com.google.errorprone.annotations.**
# Google Tink KeysDownloader 引用的可选依赖（远程密钥获取功能，本地 EncryptedSharedPreferences 不使用）
-dontwarn com.google.api.client.http.**
-dontwarn com.google.api.client.http.javanet.**
-dontwarn org.joda.time.**
# Google Tink（security-crypto 依赖，引用 errorprone 注解）
-keep class com.google.crypto.tink.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable, Exceptions, Deprecated, RuntimeVisibleAnnotations, AnnotationDefault

# === Kotlin 元数据 ===
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations

# === Kotlin 协程 ===
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }

# === Kotlinx Serialization ===
# 保留 @Serializable 注解的类及其 Companion/serializer
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class com.linkall.android.**$$serializer { *; }
-keepclassmembers class com.linkall.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.linkall.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# 通用序列化保护（覆盖所有 @Serializable 类）
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    static <methods>;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

# === Retrofit / OkHttp ===
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
# Retrofit 2.11 + kotlinx-serialization converter 反射
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# === Koin ===
-keep class org.koin.** { *; }
-dontwarn org.koin.**
-keep class * extends org.koin.core.module.Module { *; }

# === WebRTC（关键：保留原生库反射调用）===
-keep class org.webrtc.** { *; }
-keep class org.webrtc.**$* { *; }
-dontwarn org.webrtc.**
-keepclassmembers class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** {
    native <methods>;
}
# WebRTC 通过 Class.forName 加载
-keep class org.webrtc.PeerConnectionFactory { *; }
-keep class org.webrtc.audio.JavaAudioDeviceModule { *; }
-keep class org.webrtc.DefaultVideoEncoderFactory { *; }
-keep class org.webrtc.DefaultVideoDecoderFactory { *; }
-keep class org.webrtc.ScreenCapturerAndroid { *; }
-keep class org.webrtc.EglBase { *; }
-keep class org.webrtc.EglBase14 { *; }
-keep class org.webrtc.SurfaceTextureHelper { *; }

# === Compose ===
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# === Markwon ===
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**

# === Coil ===
-dontwarn coil.**
-keep class coil.** { *; }

# === AndroidX 通用 ===
-dontwarn androidx.appcompat.**
-keep class androidx.appcompat.app.AppCompatDelegateImpl { *; }
-keep class android.support.v4.app.** { *; }

# === DataStore ===
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# === EncryptedSharedPreferences ===
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# === 应用入口类（Manifest 引用）===
-keep class com.linkall.android.LinkALLApp { *; }
-keep class com.linkall.android.MainActivity { *; }
-keep class com.linkall.android.service.** { *; }
-keep class com.linkall.android.webrtc.** { *; }
-keep class com.linkall.android.data.model.** { *; }
-keep class com.linkall.android.data.api.LinkAllApi { *; }
-keep class com.linkall.android.data.signaling.** { *; }

# === ViewModels ===
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>();
}

# === Composable 函数 ===
-keepclassmembers class com.linkall.android.ui.** {
    *** Composable(...);
}

# === 反射调用（权限、ROM 适配 Intent 等）===
-keep class android.content.Intent { *; }
-keep class android.content.ComponentName { *; }

# === 枚举 ===
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# === native 方法 ===
-keepclasseswithmembernames class * {
    native <methods>;
}

# === View 构造函数（LayoutInflater 反射）===
-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# === Activity / Fragment ===
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Fragment
-keep public class * extends androidx.fragment.app.Fragment

# === Parcelable ===
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# === R8 完整模式 ===
# 允许 R8 优化，但保留必要的反射
-allowaccessmodification
