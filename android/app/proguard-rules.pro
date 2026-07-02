# Add project specific ProGuard rules here.

# kotlinx.serialization: los serializadores generados se referencian via
# reflexion desde el companion object de cada @Serializable.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.jhonatan.notas.**$$serializer { *; }
-keepclassmembers class com.jhonatan.notas.** {
    *** Companion;
}
-keepclasseswithmembers class com.jhonatan.notas.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Modelos serializados con kotlinx.serialization (DTOs de red).
-keep,includedescriptorclasses class com.jhonatan.notas.data.remote.**
-keep,includedescriptorclasses class com.jhonatan.notas.data.remote.**$* { *; }

# Ktor + su motor Android (OkHttp por debajo).
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
