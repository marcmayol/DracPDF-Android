package com.marcmayol.dracpdf.adaptadores.mupdf

import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.StructuredText
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.puertos.LectorDeEstructura
import com.marcmayol.dracpdf.dominio.puertos.Progreso
import kotlin.math.max
import kotlin.math.min

/**
 * Lee un documento abierto como contenido, con MuPDF.
 *
 * Sigue la regla de la casa al pie de la letra: todo pasa por el hilo del documento y
 * **nada nativo sale de aquí**. Las páginas y los textos estructurados se destruyen antes
 * de volver, y lo que cruza la puerta son las coordenadas copiadas a datos propios. Un
 * `StructuredText` guardado para procesarlo fuera se recogería en el hilo del recolector
 * y se llevaría el proceso por delante.
 *
 * **Sólo lee; no deduce nada.** Qué es un título y dónde hay una tabla lo decide
 * [DeduccionDeEstructura], que no sabe qué es MuPDF. La razón es práctica: la heurística
 * es lo que se va a tocar cuando el resultado no convenza, y tocarla no debería obligar a
 * releer nada de lo que habla con el motor.
 *
 * **No se usa `asJSON()` ni `asHTML()`** aunque los dos existan y traigan el tamaño de la
 * letra hecho. El primero obliga a analizar un JSON de vuelta —con un analizador que
 * habría que traer— para recuperar lo que ya se tiene en objetos, y el segundo entrega la
 * página calcada con posiciones absolutas, que es lo contrario de lo que hace falta aquí.
 */
class MuPdfEstructura(
    private val sesiones: SesionesMuPdf,
) : LectorDeEstructura {
    override fun estructuraDe(
        id: IdDocumento,
        progreso: Progreso,
    ): DocumentoEstructurado {
        val paginas = mutableListOf<List<LineaLeida>>()

        sesiones.en(id) { documento ->
            val total = documento.countPages()
            for (numero in 0 until total) {
                paginas += lineasDe(documento, numero)
                // Cancelar deja lo leído hasta aquí, que no es un resultado a medias: no
                // hay nada escrito todavía, y quien cancela no espera un fichero.
                if (!progreso.paso(numero + 1, total)) break
            }
        }
        return DeduccionDeEstructura.documentoDe(paginas)
    }

    /** Una página, con su hoja y su texto estructurado soltados pase lo que pase. */
    private fun lineasDe(
        documento: Document,
        numero: Int,
    ): List<LineaLeida> {
        val hoja = documento.loadPage(numero)
        return try {
            val estructurado = hoja.toStructuredText()
            try {
                lineasDe(estructurado)
            } finally {
                estructurado.destroy()
            }
        } finally {
            hoja.destroy()
        }
    }

    private fun lineasDe(estructurado: StructuredText): List<LineaLeida> {
        val lineas = mutableListOf<LineaLeida>()
        // El índice del bloque viaja con la línea: es la única pista de qué líneas venían
        // juntas, y el motor ya ha hecho ahí el trabajo de decidir dónde acaba un párrafo.
        estructurado.blocks?.forEachIndexed { indice, bloque ->
            // Un bloque de imagen no tiene líneas; en un escaneado son todos así.
            bloque?.lines?.forEach { linea -> leerLinea(indice, linea)?.let { lineas += it } }
        }
        return lineas
    }

    /**
     * Una línea partida en palabras, con dónde empieza y acaba cada una.
     *
     * Las palabras hacen falta para las tablas: sin saber dónde hay huecos no se puede
     * adivinar dónde hay columnas, y el motor no da ni palabras ni espacios, da letras
     * sueltas con su cuadrilátero.
     */
    private fun leerLinea(
        bloque: Int,
        linea: StructuredText.TextLine,
    ): LineaLeida? {
        val palabras = mutableListOf<PalabraLeida>()
        val letras = StringBuilder()
        var izquierda = 0f
        var derecha = 0f
        var altoAcumulado = 0f
        var visibles = 0

        fun cerrarPalabra() {
            if (letras.isNotEmpty()) {
                palabras += PalabraLeida(letras.toString(), izquierda, derecha)
                letras.setLength(0)
            }
        }

        linea.chars?.forEach { caracter ->
            val marco = caracter.quad
            if (marco == null || !esVisible(caracter.c)) {
                cerrarPalabra()
                return@forEach
            }
            if (letras.isEmpty()) izquierda = min(marco.ul_x, marco.ll_x)
            derecha = max(marco.ur_x, marco.lr_x)
            letras.appendCodePoint(caracter.c)
            altoAcumulado += altoDe(marco)
            visibles++
        }
        cerrarPalabra()

        if (palabras.isEmpty()) return null
        return LineaLeida(
            bloque = bloque,
            palabras = palabras,
            // La media y no el máximo: una tilde o un paréntesis alto no cambian el cuerpo
            // de la línea, y con el máximo cualquier línea con un signo raro subiría de
            // tamaño y se leería como un título.
            tamano = if (visibles > 0) altoAcumulado / visibles else 0f,
            y0 = linea.bbox?.y0 ?: 0f,
            y1 = linea.bbox?.y1 ?: 0f,
        )
    }

    /**
     * El cuadrilátero de una letra abarca de la altura de las mayúsculas al pie de las
     * descendentes, así que su alto es prácticamente el cuerpo de la fuente. No es el
     * tamaño declarado —eso no lo da el binding— pero sí es proporcional a él, y lo único
     * que se hace con estos números es compararlos entre sí.
     */
    private fun altoDe(marco: Quad): Float = max(marco.ll_y - marco.ul_y, marco.lr_y - marco.ur_y)

    /**
     * Los controles se descartan y los blancos separan palabras. El que da guerra es el
     * 0x0C con el que MuPDF cierra cada página: colado en el texto se pega invisible al
     * principio de la palabra siguiente.
     */
    private fun esVisible(punto: Int): Boolean =
        punto >= PRIMER_CARACTER_VISIBLE && !Character.isWhitespace(punto) && !Character.isSpaceChar(punto)

    private companion object {
        const val PRIMER_CARACTER_VISIBLE = 32
    }
}
