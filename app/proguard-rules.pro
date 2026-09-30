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

# --- Ktor-Server (Web-Remote-Control) auf Android ---
# io.ktor.util.debug.IntellijIdeaDebugDetector referenziert java.lang.management,
# das auf Android nicht existiert — die Referenzen werden nur vom JVM-Debugger genutzt.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# --- WebRTC (io.github.webrtc-sdk, WHIP P0-Spike) ---
# Die libjingle-JNI-Schicht ruft über RegisterNatives-Namen in die Java-Klassen
# zurück; ohne Keep-Rules entfernt R8 die von der .so referenzierten Klassen/
# Methoden (UnsatisfiedLinkError erst zur Laufzeit). P0-Messpunkt: braucht der
# Probe diese Regeln wirklich? (Erwartung: ja — das AAR bringt kein
# consumer-rules.txt mit, verifiziert per AAR-Analyse docs/whip-spike.md §4.2)
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
