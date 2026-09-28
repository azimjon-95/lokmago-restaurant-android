-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn org.slf4j.**
-keep class uz.lokmago.restaurant.data.remote.dto.** { *; }
-keepclassmembers class **$$serializer { *; }
-keep class io.socket.** { *; }

# Release: drop verbose/debug/info logging so nothing (tokens, order data) can reach logcat by accident.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
