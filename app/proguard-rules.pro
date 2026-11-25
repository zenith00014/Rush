-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.rush.app.MainActivity { *; }
-keep class com.rush.app.RushPlaybackService { *; }
-keep class com.rush.app.RushApplication { *; }

# Rhino includes desktop-JVM integration adapters. JavaBeans and JSR-223
# are not Android APIs; the Android code path does not use these adapters.
-dontwarn java.beans.**
-dontwarn javax.script.**
