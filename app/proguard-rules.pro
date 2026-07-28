# MuPDF se llama a sí mismo desde JNI: si R8 renombra o borra estas clases, la
# biblioteca nativa no encuentra sus contrapartes Java y el proceso se cae sin
# excepción Java que lo explique.
-keep class com.artifex.mupdf.fitz.** { *; }
-keepclasseswithmembernames class com.artifex.mupdf.fitz.** {
    native <methods>;
}
