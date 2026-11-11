# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.lifeos.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.lifeos.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Vosk (офлайн-распознавание) и JNA
-keep class com.sun.jna.* { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
