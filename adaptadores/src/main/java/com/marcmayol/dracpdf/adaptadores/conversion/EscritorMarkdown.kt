package com.marcmayol.dracpdf.adaptadores.conversion

import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import java.io.OutputStream

/**
 * Markdown desde la estructura, con el mismo dialecto que usa el escritorio.
 *
 * Las tablas van en el estilo de GitHub —barras y una fila de guiones— porque es el
 * único que entienden los editores de móvil que alguien va a usar para abrir esto.
 * Markdown estándar no tiene tablas, así que la alternativa era escribir HTML dentro del
 * Markdown, que se lee peor en crudo y no es lo que nadie espera.
 */
internal object EscritorMarkdown : EscritorDeDocumento {
    override fun escribir(
        documento: DocumentoEstructurado,
        salida: OutputStream,
    ) {
        val texto =
            documento.bloques
                .mapNotNull(::trozoDe)
                .filter { it.isNotBlank() }
                .joinToString(SEPARACION)

        salida.write(texto.toByteArray(Charsets.UTF_8))
        if (texto.isNotEmpty()) salida.write(FIN_DE_LINEA)
    }

    private fun trozoDe(bloque: BloqueDeTexto): String? =
        when (bloque) {
            // El nivel se topa en seis porque Markdown no tiene más; un documento con
            // siete tamaños de título distintos existe, y el séptimo se escribe como el
            // sexto en vez de dejar un `#######` que ningún lector reconoce.
            is BloqueDeTexto.Titulo -> "#".repeat(bloque.nivel.coerceIn(1, NIVEL_MAXIMO)) + " " + bloque.texto.trim()
            is BloqueDeTexto.Parrafo -> bloque.texto.trim()
            is BloqueDeTexto.Tabla -> tablaDe(bloque)
            is BloqueDeTexto.SaltoDePagina -> CORTE_DE_PAGINA
        }

    private fun tablaDe(tabla: BloqueDeTexto.Tabla): String {
        val filas = tabla.filasCuadradas()
        if (filas.isEmpty()) return ""
        val columnas = filas.first().size
        // La primera fila hace de cabecera porque Markdown **obliga** a que haya una: una
        // tabla sin fila de guiones no se dibuja como tabla en ningún lector. En una
        // tabla deducida por posición esa primera fila suele ser la cabecera de verdad.
        val separadora = List(columnas) { "---" }
        return (listOf(filas.first(), separadora) + filas.drop(1)).joinToString("\n", transform = ::filaDe)
    }

    /** Las barras del contenido se escapan: sin eso, una celda parte la tabla en dos. */
    private fun filaDe(fila: List<String>): String =
        fila.joinToString(" | ", prefix = "| ", postfix = " |") { it.replace("|", "\\|") }

    private const val SEPARACION = "\n\n"
    private const val CORTE_DE_PAGINA = "---"
    private const val NIVEL_MAXIMO = 6
    private val FIN_DE_LINEA = "\n".toByteArray(Charsets.UTF_8)
}
