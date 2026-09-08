# What R8 has to be told, and why. Everything here is a lookup R8 cannot see:
# a class named in a string, a service file, or a member reached reflectively.

# A crash note names the class and line it came from - see DIAGNOSTICS.md - so
# the trace has to survive obfuscation. mapping.txt is kept per release.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Ktor. translationHttpClient names no engine, so the engine is found through
# META-INF/services at runtime; R8 drops the container otherwise and every
# translation fails with "Failed to find HTTP client engine implementation".
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer { *; }
-keep,allowobfuscation class * implements io.ktor.client.HttpClientEngineContainer
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.**

# pdfbox-android resolves filters, fonts and COSName constants by name. Broad on
# purpose: this is the biggest dependency in the app and so the most tempting to
# shrink, and a narrower rule is only ever proved right by opening a real PDF on
# a real device out of a build R8 has actually been through.
#
# That run exists - `:baselineprofile:connectedBenchmarkReleaseAndroidTest` drives
# the shrunk app, and BUILD.md sets out the whole check. Narrow these when
# somebody has run it against the narrowing, and not before: a keep rule that is
# one class too tight fails as a blank page in somebody's book, months later,
# with nothing in the build to have caught it.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.**
-dontwarn javax.naming.**

# kotlinx.serialization. The generated serializers are reached through the
# companion, which R8 cannot see being used.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class io.lukia.**$$serializer { *; }
-keepclassmembers class io.lukia.** { *** Companion; }
-keepclasseswithmembers class io.lukia.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# SQLDelight needs nothing: its generated code calls straight through, and the
# driver reaches SQLite through the framework rather than by name.
