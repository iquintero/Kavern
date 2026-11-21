# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# Tink (via androidx.security) references annotations that are not on the classpath.
-dontwarn com.google.errorprone.annotations.**

# Moshi's reflective Kotlin adapter reads property names and constructors through Kotlin metadata, and it is
# used on app classes outside the API DTOs too (e.g. the cached series detail). Obfuscating or trimming any
# of them silently breaks parsing, so keep the app's own classes intact. Libraries are still shrunk.
-keep class com.iquintero.kavern.** { *; }
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
