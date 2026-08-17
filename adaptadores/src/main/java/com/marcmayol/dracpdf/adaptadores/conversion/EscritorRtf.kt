package com.marcmayol.dracpdf.adaptadores.conversion

import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import java.io.OutputStream

/**
 * Rich Text Format (.rtf) escrito a mano, que es texto con llaves.
 *
 * **El fichero entero es ASCII**, y no por nostalgia: RTF no declara codificación, así
 * que un fichero con bytes UTF-8 sueltos se abre con los acentos rotos en cualquier
 * lector que respete el formato. Lo que no es ASCII va escapado como `\uN?`, con un
 * sustituto detrás para los lectores viejos que no entiendan el escape.
 *
 * Se conserva desde el escritorio hasta el detalle de los tamaños en medios puntos, que
 * es como RTF mide la letra.
 */
internal object EscritorRtf : EscritorDeDocumento {
    override fun escribir(
        documento: DocumentoEstructurado,
        salida: OutputStream,
    ) {
        val rtf =
            buildString {
                append(CABECERA)
                documento.bloques.forEach { bloque -> append(marcaDe(bloque)) }
                append("}\n")
            }
        salida.write(rtf.toByteArray(Charsets.US_ASCII))
    }

    private fun marcaDe(bloque: BloqueDeTexto): String =
        when (bloque) {
            is BloqueDeTexto.Titulo -> {
                val tamano = TAMANOS_DE_TITULO.getOrElse(bloque.nivel - 1) { TAMANOS_DE_TITULO.last() }
                "\\pard\\sb200\\sa100\\b\\fs$tamano ${escapar(bloque.texto.trim())}\\b0\\fs$TAMANO_CUERPO\\par\n"
            }

            is BloqueDeTexto.Parrafo ->
                "\\pard\\sa100\\fs$TAMANO_CUERPO ${escapar(bloque.texto.trim())}\\par\n"

            is BloqueDeTexto.Tabla -> tablaDe(bloque)
            is BloqueDeTexto.SaltoDePagina -> "\\page\n"
        }

    private fun tablaDe(tabla: BloqueDeTexto.Tabla): String {
        val filas = tabla.filasCuadradas()
        if (filas.isEmpty()) return ""
        val columnas = filas.first().size
        val ancho = ANCHO_UTIL_TWIPS / columnas
        return buildString {
            filas.forEach { fila ->
                append("\\trowd\\trgaph100")
                // Las columnas de RTF se declaran por su borde derecho acumulado, no por
                // su anchura: `cellx` es «hasta aquí llega esta celda».
                repeat(columnas) { indice -> append("\\cellx${ancho * (indice + 1)}") }
                append("\n")
                fila.forEach { celda -> append("\\pard\\intbl\\fs$TAMANO_CUERPO ${escapar(celda)}\\cell") }
                append("\\row\n")
            }
            append("\\pard\n")
        }
    }

    /**
     * Lo que RTF no admite en crudo: sus tres caracteres propios y todo lo que pase de
     * ASCII.
     */
    private fun escapar(texto: String): String =
        buildString(texto.length) {
            texto.forEach { caracter ->
                when {
                    caracter == '\\' || caracter == '{' || caracter == '}' -> append('\\').append(caracter)
                    caracter == '\n' || caracter == '\r' -> append("\\line ")
                    caracter.code < PRIMER_NO_ASCII -> append(caracter)
                    // `\u` lleva el código **con signo**, que es la herencia de cuando el
                    // parámetro de un comando RTF era un entero de 16 bits.
                    else -> append("\\u").append(conSigno(caracter.code)).append('?')
                }
            }
        }

    private fun conSigno(codigo: Int): Int = if (codigo > MAYOR_POSITIVO) codigo - VUELTA_DE_16_BITS else codigo

    private const val CABECERA = "{\\rtf1\\ansi\\ansicpg1252\\deff0{\\fonttbl{\\f0\\fswiss Helvetica;}}\n"

    /** En medios puntos: 22 son 11 puntos, el cuerpo de toda la vida. */
    private const val TAMANO_CUERPO = 22
    private val TAMANOS_DE_TITULO = listOf(32, 28, 24)

    /** El ancho útil de un A4 con márgenes normales, en twips. */
    private const val ANCHO_UTIL_TWIPS = 9000

    private const val PRIMER_NO_ASCII = 128
    private const val MAYOR_POSITIVO = 32767
    private const val VUELTA_DE_16_BITS = 65536
}
