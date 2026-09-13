# Supabase e Ktor usano la serializzazione: si tengono i serializzatori generati.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class it.cyb3rdm0n.convocami.**$$serializer { *; }
-keepclassmembers class it.cyb3rdm0n.convocami.** { *** Companion; }

# Il motore di sorteggio non usa reflection: non serve tenere altro.
