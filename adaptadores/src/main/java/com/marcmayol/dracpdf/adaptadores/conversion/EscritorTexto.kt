package com.marcmayol.dracpdf.adaptadores.conversion

import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import java.io.OutputStream

/**
 * Texto plano desde la estructura.
 *
 * Existe además del volcado crudo de `HerramientasPdf.aTexto` y no lo repite: aquel saca
 * las líneas tal como el motor las entrega, y éste sale de la misma deducción que el
 * Markdown y el ODT, así que un título queda separado de su párrafo y una tabla sale
 * como columnas y no como palabras sueltas. Los dos son legítimos; éste es el que
 * acompaña al resto de conversiones.
 *
 * **No se subrayan los títulos ni se dibujan las tablas con barras.** Eso sería Markdown
 * mal escrito: quien pide texto plano quiere el contenido sin adornos, y para lo otro
 * está el formato que lo hace bien.
 */
internal object EscritorTexto : EscritorDeDocumento {
    override fun escribir(
        documento: DocumentoEstructurado,
        salida: OutputStream,
    ) {
        val texto =
            documento.bloques
                .mapNotNull { bloque ->
                    when (bloque) {
                        // Una línea de guiones donde acaba la hoja: el 0x0C que MuPDF usa
                        // se pega invisible al principio de la línea siguiente.
                        is BloqueDeTexto.SaltoDePagina -> CORTE_DE_PAGINA
                        else -> bloque.textoLlano().takeIf { it.isNotBlank() }
                    }
                }.joinToString(SEPARACION)

        salida.write(texto.toByteArray(Charsets.UTF_8))
        if (texto.isNotEmpty()) salida.write(FIN_DE_LINEA)
    }

    /** Una línea en blanco entre bloques, que es lo que separa párrafos en texto plano. */
    private const val SEPARACION = "\n\n"
    private const val CORTE_DE_PAGINA = "---"
    private val FIN_DE_LINEA = "\n".toByteArray(Charsets.UTF_8)
}
