# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.lifeos.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.lifeos.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
