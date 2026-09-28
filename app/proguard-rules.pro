-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn javax.servlet.**
-dontwarn org.apache.**
-dontwarn coil.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn com.google.errorprone.**
-dontwarn org.slf4j.impl.**

# Keep inherited services.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

# With R8 full mode generic signatures are stripped for classes that are not
# kept. Suspend functions are wrapped in continuations where the type argument
# is used.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# R8 full mode strips generic signatures from return types if not kept.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# The Sentry SDK reaches for API 35 classes (app-start metrics, Perfetto
# profiling) behind runtime SDK_INT checks, but compileSdk is 34, so R8 cannot
# resolve them. Drop these when compileSdk reaches 35.
-dontwarn android.app.ApplicationStartInfo
-dontwarn android.os.ProfilingManager
-dontwarn android.os.ProfilingResult
