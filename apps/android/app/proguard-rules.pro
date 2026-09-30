-dontwarn org.bouncycastle.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.sun.jna.**
-dontwarn javax.naming.**
-dontwarn lombok.Generated
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn sun.net.spi.nameservice.NameServiceDescriptor

# Native TeX engine. Its font tables are described in assets/*.xml and the glyph
# mapping is resolved by name at runtime, so reflective entry points and the
# ContentProvider that loads them must survive shrinking.
-keep class ru.noties.jlatexmath.** { *; }
-keep class org.scilab.forge.jlatexmath.** { *; }
-keepclassmembers class org.scilab.forge.jlatexmath.** {
  public <init>(...);
  public <methods>;
}
-keepnames class ru.noties.jlatexmath.JLatexMathInitProvider
-dontwarn org.scilab.forge.jlatexmath.**
-dontwarn ru.noties.jlatexmath.**
