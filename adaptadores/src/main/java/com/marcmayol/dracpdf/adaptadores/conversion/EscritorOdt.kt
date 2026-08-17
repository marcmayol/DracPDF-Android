package com.marcmayol.dracpdf.adaptadores.conversion

import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import java.io.OutputStream

/**
 * OpenDocument Text (.odt) escrito a mano, sin bibliotecas.
 *
 * Un ODT es un ZIP con XML dentro y cuatro entradas obligatorias; el escritorio llegó a
 * la misma conclusión y lo escribe igual. Traerse una biblioteca de ofimática a un APK
 * para producir cuatro ficheros de texto sería pagar megabytes por lo que caben en esta
 * clase.
 *
 * Lo que se promete es el **contenido**: títulos con su nivel de esquema, párrafos y
 * tablas. La maquetación del PDF no se conserva, y por eso el resultado sale etiquetado
 * «reformateado».
 */
internal object EscritorOdt : EscritorDeDocumento {
    /** El tipo MIME va también dentro del paquete, y tiene que coincidir con éste. */
    const val TIPO_MIME = "application/vnd.oasis.opendocument.text"

    override fun escribir(
        documento: DocumentoEstructurado,
        salida: OutputStream,
    ) {
        PaqueteZip(salida).use { paquete ->
            // Primera entrada y sin comprimir, como manda la especificación.
            paquete.crudo("mimetype", TIPO_MIME)
            paquete.comprimido("META-INF/manifest.xml", MANIFIESTO)
            paquete.comprimido("styles.xml", ESTILOS)
            paquete.comprimido("content.xml", CABECERA + cuerpoDe(documento) + PIE)
        }
    }

    private fun cuerpoDe(documento: DocumentoEstructurado): String =
        buildString { documento.bloques.forEach { bloque -> append(marcaDe(bloque)) } }

    private fun marcaDe(bloque: BloqueDeTexto): String =
        when (bloque) {
            is BloqueDeTexto.Titulo -> {
                val nivel = bloque.nivel.coerceAtLeast(1)
                "      <text:h text:outline-level=\"$nivel\">${bloque.texto.trim().escapadoXml()}</text:h>\n"
            }

            is BloqueDeTexto.Parrafo -> "      <text:p>${bloque.texto.trim().escapadoXml()}</text:p>\n"
            is BloqueDeTexto.Tabla -> tablaDe(bloque)
            // Un párrafo vacío con salto de página forzado antes: es la única manera que
            // tiene ODF de decir «aquí acababa la hoja» sin inventarse una maquetación.
            is BloqueDeTexto.SaltoDePagina -> "      <text:p text:style-name=\"CorteDePagina\"/>\n"
        }

    private fun tablaDe(tabla: BloqueDeTexto.Tabla): String {
        val filas = tabla.filasCuadradas()
        if (filas.isEmpty()) return ""
        return buildString {
            append("      <table:table table:name=\"Tabla de la página ${tabla.pagina + 1}\">\n")
            append("        <table:table-column table:number-columns-repeated=\"${filas.first().size}\"/>\n")
            filas.forEach { fila ->
                append("        <table:table-row>\n")
                fila.forEach { celda ->
                    append("          <table:table-cell office:value-type=\"string\">")
                    append("<text:p>${celda.escapadoXml()}</text:p></table:table-cell>\n")
                }
                append("        </table:table-row>\n")
            }
            append("      </table:table>\n")
        }
    }

    private val MANIFIESTO =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <manifest:manifest
            xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0"
            manifest:version="1.3">
          <manifest:file-entry manifest:full-path="/" manifest:media-type="$TIPO_MIME"/>
          <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
          <manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>
        </manifest:manifest>
        """.trimIndent()

    private val ESTILOS =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <office:document-styles
            xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
            xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
            xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
            office:version="1.3">
          <office:styles>
            <style:style style:name="Standard" style:family="paragraph"/>
          </office:styles>
        </office:document-styles>
        """.trimIndent()

    private val CABECERA =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <office:document-content
            xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
            xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
            xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
            xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
            xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
            office:version="1.3">
          <office:automatic-styles>
            <style:style style:name="CorteDePagina" style:family="paragraph">
              <style:paragraph-properties fo:break-before="page"/>
            </style:style>
          </office:automatic-styles>
          <office:body>
            <office:text>
        """.trimIndent() + "\n"

    private val PIE =
        """
            </office:text>
          </office:body>
        </office:document-content>
        """.trimIndent() + "\n"
}
