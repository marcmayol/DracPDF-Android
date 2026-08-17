package com.marcmayol.dracpdf.ui.herramientas

import com.marcmayol.dracpdf.dominio.puertos.FormatoSalida
import com.marcmayol.dracpdf.dominio.puertos.FormatoTabla

/**
 * A qué se puede convertir el documento, dicho como lo diría el usuario.
 *
 * No es el enumerado del dominio y no debería serlo: aquí «Word» es una cosa y allí es
 * un `.docx`, aquí «Tablas» es una cosa y allí son dos formatos distintos. Esta lista
 * es la de la pantalla, y su orden es el de lo más pedido a lo menos.
 *
 * Los tres primeros salen a un fichero y el resto a una carpeta, y esa diferencia no
 * es un capricho de la implementación: unas tablas son varios ficheros y unas imágenes
 * son una por página, así que preguntar «dónde lo guardo» con un solo nombre sería
 * mentir sobre lo que va a pasar.
 */
enum class DestinoDeConversion(
    val etiqueta: String,
    /** Lo que hay que decirle al usuario de este destino, si hay algo. */
    val explicacion: String,
) {
    TEXTO("Texto", "Un fichero .txt con lo que el documento lleve escrito."),
    WORD("Word", "Un .docx con el texto, los títulos y las tablas. La maquetación no se conserva."),
    IMAGENES("Imágenes", "Una imagen por página, en la carpeta que elijas."),
    HTML("HTML", "Una página web con los títulos y los párrafos del documento."),
    MARKDOWN("Markdown", "Texto con marcas: los títulos salen como #, ## y ###."),
    ODT("ODT", "El documento de LibreOffice y OpenOffice."),
    RTF("RTF", "Texto con formato, que abre casi cualquier procesador."),
    TABLAS("Tablas", "Las tablas del documento, una hoja o un fichero por tabla."),
    ;

    /** Si escribe en una carpeta en vez de en un fichero elegido a mano. */
    val vaACarpeta: Boolean get() = this != TEXTO && this != WORD

    /** El formato del dominio, para los que son un documento de texto. */
    val formato: FormatoSalida?
        get() =
            when (this) {
                HTML -> FormatoSalida.HTML
                MARKDOWN -> FormatoSalida.MARKDOWN
                ODT -> FormatoSalida.ODT
                RTF -> FormatoSalida.RTF
                else -> null
            }

    val tag: String get() = "convertir_a_${name.lowercase()}"
}

/** Los dos formatos de tabla, con lo que los distingue para quien elige. */
enum class DestinoDeTabla(
    val etiqueta: String,
    val formato: FormatoTabla,
) {
    CSV("CSV", FormatoTabla.CSV),
    XLSX("XLSX", FormatoTabla.XLSX),
    ;

    val tag: String get() = "convertir_tabla_${name.lowercase()}"
}
