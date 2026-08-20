-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# kotlinx.serialization keeps generated serializers reachable
-keepclassmembers class org.sovereignhq.nightjar.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class org.sovereignhq.nightjar.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
