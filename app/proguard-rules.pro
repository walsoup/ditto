# ProGuard / R8 rules for Ditto

# Keep Kotlinx Serialization models & serializers
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Compose runtime annotations
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# R8 optimization flags
-repackageclasses ''
-allowaccessmodification
