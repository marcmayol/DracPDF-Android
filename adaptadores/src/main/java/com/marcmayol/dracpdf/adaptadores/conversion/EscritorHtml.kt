package com.marcmayol.dracpdf.adaptadores.conversion

import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import java.io.OutputStream

/**
 * HTML desde la estructura.
 *
 * **No se usa el `asHTML()` del motor** aunque exista y sea gratis: lo que devuelve es la
 * página calcada con posiciones absolutas y tamaños en píxeles, o sea un facsímil que no
 * se puede leer en una pantalla estrecha ni copiar en ningún sitio. Aquí se quiere lo
 * contrario —contenido que fluya— y eso sale de la misma deducción que el resto de
 * formatos, que además garantiza que el HTML y el ODT digan lo mismo.
 *
 * El fichero es **uno solo y sin nada fuera**: ni hojas de estilo aparte ni imágenes
 * extraídas. Un HTML que arrastra una carpeta al lado se rompe en cuanto alguien lo
 * comparte por mensajería, que es lo que se hace con un fichero en un móvil.
 */
internal object EscritorHtml : EscritorDeDocumento {
    override fun escribir(
        documento: DocumentoEstructurado,
        salida: OutputStream,
    ) {
        val html =
            buildString {
                append(cabeceraCon(documento.tituloProbable()))
                documento.bloques.forEach { bloque -> append(marcaDe(bloque)) }
                append(PIE)
            }
        salida.write(html.toByteArray(Charsets.UTF_8))
    }

    private fun marcaDe(bloque: BloqueDeTexto): String =
        when (bloque) {
            is BloqueDeTexto.Titulo -> {
                val nivel = bloque.nivel.coerceIn(1, NIVEL_MAXIMO)
                "  <h$nivel>${bloque.texto.trim().escapadoXml()}</h$nivel>\n"
            }

            is BloqueDeTexto.Parrafo ->
                bloque.texto
                    .trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { "  <p>${it.escapadoXml()}</p>\n" } ?: ""

            is BloqueDeTexto.Tabla -> tablaDe(bloque)
            // Una regla horizontal y no un salto de impresión: esto se lee en pantalla,
            // donde el corte de la hoja original sólo sirve para situarse.
            is BloqueDeTexto.SaltoDePagina -> "  <hr>\n"
        }

    private fun tablaDe(tabla: BloqueDeTexto.Tabla): String =
        buildString {
            append("  <table>\n")
            tabla.filasCuadradas().forEachIndexed { indice, fila ->
                // La primera fila como cabecera: es lo que casi siempre es, y si se
                // equivoca el único coste es una fila en negrita de más.
                val etiqueta = if (indice == 0) "th" else "td"
                append("    <tr>")
                fila.forEach { celda -> append("<$etiqueta>${celda.escapadoXml()}</$etiqueta>") }
                append("</tr>\n")
            }
            append("  </table>\n")
        }

    /**
     * El estilo va dentro y es corto a propósito: lo justo para que las tablas tengan
     * bordes y el texto no cruce una tablet de lado a lado. Nada que imite el PDF.
     */
    private fun cabeceraCon(titulo: String): String =
        """
        <!DOCTYPE html>
        <html lang="es">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>${titulo.escapadoXml()}</title>
        <style>
        body { font-family: serif; line-height: 1.5; margin: 0 auto; max-width: 40em; padding: 1em; }
        table { border-collapse: collapse; margin: 1em 0; }
        th, td { border: 1px solid #999; padding: 0.3em 0.6em; text-align: left; }
        </style>
        </head>
        <body>
        """.trimIndent() + "\n"

    private const val PIE = "</body>\n</html>\n"
    private const val NIVEL_MAXIMO = 6
}
