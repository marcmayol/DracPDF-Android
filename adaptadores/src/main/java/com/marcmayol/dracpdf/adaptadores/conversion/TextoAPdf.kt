package com.marcmayol.dracpdf.adaptadores.conversion

import com.artifex.mupdf.fitz.Buffer
import com.artifex.mupdf.fitz.Font
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFObject
import com.artifex.mupdf.fitz.Rect
import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Texto ya entendido, compuesto en hojas A4.
 *
 * **El salto de página lo decide esto y no el que escribió el fichero.** Un `.txt` o un
 * Markdown no tienen páginas: son una tira de texto sin fin, y componerla es ir midiendo
 * líneas hasta que la hoja se acaba. Por eso hay medición de verdad —se le pregunta a la
 * fuente cuánto ocupa cada letra— y no un cálculo por número de caracteres: con «Illinois»
 * y «MMMMMMMM» ocupando lo mismo, media página se saldría por el margen derecho.
 *
 * **Los acentos.** Las fuentes simples de un PDF direccionan sus glifos por byte, y la
 * codificación que se les pone aquí es WinAnsi, donde «á» es el byte 0xE1 y no los dos
 * que ocupa en UTF-8. Escribir el flujo de contenido en UTF-8 —que es lo que sale de
 * pasarle una `String` al motor— convierte cada acento en dos símbolos raros. De ahí que
 * el contenido de la página se monte como **bytes** y se entregue en un `Buffer`: es la
 * única forma de controlar qué byte acaba dentro de cada paréntesis.
 *
 * Se crea uno por documento de salida y hay que [cerrar]lo: lleva dos fuentes del motor,
 * que son memoria nativa y no las recoge nadie.
 */
internal class TextoAPdf(
    private val pdf: PDFDocument,
) {
    private val redonda = Font(FUENTE_REDONDA)
    private val negrita = Font(FUENTE_NEGRITA)

    /**
     * El diccionario de recursos, uno solo para todas las páginas del documento.
     *
     * Compartirlo no es tacañería de bytes: con un diccionario por hoja, cada página
     * arrastraría su propia copia de la fuente y un documento de cuarenta páginas pesaría
     * cuarenta veces lo que debe.
     */
    private val recursos: PDFObject =
        pdf.newDictionary().apply {
            put(
                "Font",
                pdf.newDictionary().apply {
                    put(REDONDA, pdf.addSimpleFont(redonda, Font.SIMPLE_ENCODING_LATIN))
                    put(NEGRITA, pdf.addSimpleFont(negrita, Font.SIMPLE_ENCODING_LATIN))
                },
            )
        }

    /**
     * Escribe [documento] al final del PDF, en tantas hojas como haga falta.
     *
     * @return cuántas páginas ha añadido; 0 si no había nada que escribir, que es lo que
     *   pasa con un fichero vacío y no es un fallo que merezca una excepción.
     */
    fun anadir(documento: DocumentoEstructurado): Int {
        val hoja = Composicion()
        documento.bloques.forEach(hoja::escribir)
        hoja.cerrarPagina()
        return hoja.paginas
    }

    fun cerrar() {
        redonda.destroy()
        negrita.destroy()
    }

    /**
     * Cómo se pinta cada clase de bloque.
     *
     * El nivel del título no cambia sólo el tamaño: cambia también el aire que lo separa
     * de lo anterior, que es lo que hace que un apartado se lea como un apartado y no
     * como una frase en negrita perdida a mitad de página.
     */
    private fun estiloDe(bloque: BloqueDeTexto): Estilo =
        when (bloque) {
            is BloqueDeTexto.Titulo ->
                when (bloque.nivel) {
                    1 -> Estilo(negrita, NEGRITA, TAMANO_TITULO_1, INTERLINEADO_TITULO_1, AIRE_TITULO_1)
                    2 -> Estilo(negrita, NEGRITA, TAMANO_TITULO_2, INTERLINEADO_TITULO_2, AIRE_TITULO_2)
                    else -> Estilo(negrita, NEGRITA, TAMANO_TITULO_3, INTERLINEADO_TITULO_3, AIRE_TITULO_3)
                }

            else -> Estilo(redonda, REDONDA, TAMANO_CUERPO, INTERLINEADO_CUERPO, AIRE_PARRAFO)
        }

    /** Una fuente, su tamaño, lo que baja cada línea y el aire que deja detrás. */
    private data class Estilo(
        val fuente: Font,
        val nombre: String,
        val tamano: Float,
        val interlineado: Float,
        val aireDetras: Float,
    )

    /**
     * La hoja que se está llenando ahora mismo.
     *
     * Es una clase y no un puñado de variables sueltas porque el estado es el que es —qué
     * llevamos escrito y por qué altura vamos— y tenerlo junto es lo que permite que
     * «escribir una línea» sea una sola operación que sabe partir la página sola.
     */
    private inner class Composicion {
        var paginas = 0
            private set

        private var contenido = ByteArrayOutputStream()
        private var altura = HojaA4.ALTO - MARGEN
        private var hayTinta = false

        fun escribir(bloque: BloqueDeTexto) {
            when (bloque) {
                // Los saltos que traía el original se respetan: si quien lo escribió puso
                // un corte ahí, es porque significa algo.
                is BloqueDeTexto.SaltoDePagina -> cerrarPagina()
                is BloqueDeTexto.Titulo -> parrafo(bloque.texto, estiloDe(bloque), AIRE_ANTES_DEL_TITULO)
                is BloqueDeTexto.Parrafo -> parrafo(bloque.texto, estiloDe(bloque), 0f)
                // Una tabla no se dibuja con líneas: se escriben sus filas. Componer una
                // tabla de verdad en A4 es un problema entero —anchos de columna, celdas
                // que no caben, cabeceras que se repiten— y prometerlo a medias sería
                // peor que ser claro con lo que hay.
                is BloqueDeTexto.Tabla ->
                    bloque.filasCuadradas().forEach { fila ->
                        parrafo(fila.joinToString(SEPARADOR_DE_CELDAS), estiloDe(bloque), 0f)
                    }
            }
        }

        private fun parrafo(
            texto: String,
            estilo: Estilo,
            aireDelante: Float,
        ) {
            val limpio = texto.trim()
            if (limpio.isEmpty()) return
            // El aire de delante no se deja al principio de una hoja: un título que abre
            // página tiene que empezar donde empieza la página, no dos centímetros más
            // abajo que los de las hojas anteriores.
            if (hayTinta) altura -= aireDelante
            envolver(limpio, estilo).forEach { linea -> escribirLinea(linea, estilo) }
            altura -= estilo.aireDetras
        }

        private fun escribirLinea(
            linea: String,
            estilo: Estilo,
        ) {
            if (altura - estilo.interlineado < MARGEN) cerrarPagina()
            altura -= estilo.interlineado
            contenido.enAscii("BT /${estilo.nombre} ${estilo.tamano.enPdf()} Tf ")
            contenido.enAscii("${MARGEN.enPdf()} ${altura.enPdf()} Td ")
            contenido.write(cadenaPdf(linea))
            contenido.enAscii(" Tj ET\n")
            hayTinta = true
        }

        /**
         * Escribe la parte del flujo que es puro lenguaje del PDF.
         *
         * Va aparte de [cadenaPdf] porque son dos cosas distintas: los operadores son
         * ASCII y no admiten otra cosa, y el texto del usuario es WinAnsi. Mezclarlos en
         * una sola conversión es exactamente el fallo que se quiere evitar.
         */
        private fun ByteArrayOutputStream.enAscii(texto: String) = write(texto.toByteArray(Charsets.US_ASCII))

        /** Cierra la hoja en curso y la mete en el documento. Una hoja en blanco no cuenta. */
        fun cerrarPagina() {
            if (!hayTinta) return
            val buffer = Buffer()
            try {
                buffer.writeBytes(contenido.toByteArray())
                pdf.insertPage(-1, pdf.addPage(Rect(0f, 0f, HojaA4.ANCHO, HojaA4.ALTO), 0, recursos, buffer))
            } finally {
                buffer.destroy()
            }
            paginas++
            contenido = ByteArrayOutputStream()
            altura = HojaA4.ALTO - MARGEN
            hayTinta = false
        }
    }

    /**
     * Parte un párrafo en las líneas que caben, cortando por espacios.
     *
     * La palabra que no cabe entera ni en una línea vacía se parte por letras. Es feo y
     * no debería pasar nunca con texto normal, pero pasa con una dirección web larga
     * pegada en un `.txt`, y la alternativa es que se salga de la hoja sin más.
     */
    private fun envolver(
        texto: String,
        estilo: Estilo,
    ): List<String> {
        val disponible = HojaA4.ANCHO - 2 * MARGEN
        val lineas = mutableListOf<String>()
        val enCurso = StringBuilder()

        texto.split(' ', '\t').filter { it.isNotEmpty() }.forEach { palabra ->
            val candidata = if (enCurso.isEmpty()) palabra else "$enCurso $palabra"
            when {
                ancho(candidata, estilo) <= disponible -> {
                    enCurso.setLength(0)
                    enCurso.append(candidata)
                }

                else -> {
                    if (enCurso.isNotEmpty()) lineas += enCurso.toString()
                    enCurso.setLength(0)
                    val trozos = partirPalabra(palabra, estilo, disponible)
                    lineas += trozos.dropLast(1)
                    enCurso.append(trozos.last())
                }
            }
        }
        if (enCurso.isNotEmpty()) lineas += enCurso.toString()
        return lineas
    }

    /** Una palabra más ancha que la línea, cortada por donde deje de caber. */
    private fun partirPalabra(
        palabra: String,
        estilo: Estilo,
        disponible: Float,
    ): List<String> {
        if (ancho(palabra, estilo) <= disponible) return listOf(palabra)
        val trozos = mutableListOf<String>()
        val enCurso = StringBuilder()
        palabra.forEach { letra ->
            if (enCurso.isNotEmpty() && ancho("$enCurso$letra", estilo) > disponible) {
                trozos += enCurso.toString()
                enCurso.setLength(0)
            }
            enCurso.append(letra)
        }
        trozos += enCurso.toString()
        return trozos
    }

    /**
     * Cuánto mide un texto en puntos, preguntándoselo a la fuente glifo a glifo.
     *
     * Las anchuras que devuelve el motor van en «em»: 1.0 es el tamaño de la letra, así
     * que multiplicar por el cuerpo da los puntos. Es la misma cuenta que hace el propio
     * PDF al pintar, y por eso el corte de línea coincide con lo que se ve.
     */
    private fun ancho(
        texto: String,
        estilo: Estilo,
    ): Float {
        var suma = 0f
        var posicion = 0
        while (posicion < texto.length) {
            val punto = texto.codePointAt(posicion)
            posicion += Character.charCount(punto)
            suma += estilo.fuente.advanceGlyph(estilo.fuente.encodeCharacter(punto), false)
        }
        return suma * estilo.tamano
    }

    /**
     * Un texto como cadena literal del PDF: entre paréntesis y en bytes WinAnsi.
     *
     * Los tres caracteres que se escapan son los tres que el formato reserva. Lo que no
     * cabe en WinAnsi —un emoji, un ideograma— sale como `?`, que es lo que hace la
     * plataforma al codificar y aquí es lo honesto: la fuente Helvetica tampoco tiene ese
     * glifo, así que fingir lo contrario sólo cambiaría un signo raro por un hueco.
     */
    private fun cadenaPdf(texto: String): ByteArray {
        val bytes = texto.toByteArray(WIN_ANSI)
        val salida = ByteArrayOutputStream(bytes.size + 2)
        salida.write(ABRE_PARENTESIS)
        bytes.forEach { byte ->
            if (byte == ABRE_PARENTESIS.toByte() || byte == CIERRA_PARENTESIS.toByte() || byte == BARRA.toByte()) {
                salida.write(BARRA)
            }
            salida.write(byte.toInt())
        }
        salida.write(CIERRA_PARENTESIS)
        return salida.toByteArray()
    }

    private companion object {
        /**
         * Las dos de las catorce fuentes que todo lector de PDF conoce. No hace falta
         * embeber nada exótico para escribir un párrafo, y una fuente estándar abre igual
         * en el móvil del usuario que en el ordenador de quien reciba el documento.
         */
        const val FUENTE_REDONDA = "Helvetica"
        const val FUENTE_NEGRITA = "Helvetica-Bold"

        const val REDONDA = "F1"
        const val NEGRITA = "F2"

        /** Dos centímetros largos por los cuatro lados: el margen de una carta. */
        const val MARGEN = 56f

        const val TAMANO_CUERPO = 11f
        const val INTERLINEADO_CUERPO = 15.4f
        const val AIRE_PARRAFO = 7f

        const val TAMANO_TITULO_1 = 20f
        const val INTERLINEADO_TITULO_1 = 25f
        const val AIRE_TITULO_1 = 6f

        const val TAMANO_TITULO_2 = 15f
        const val INTERLINEADO_TITULO_2 = 19f
        const val AIRE_TITULO_2 = 5f

        const val TAMANO_TITULO_3 = 12.5f
        const val INTERLINEADO_TITULO_3 = 16f
        const val AIRE_TITULO_3 = 4f

        /** Lo que un título respira por encima, para que se despegue de lo anterior. */
        const val AIRE_ANTES_DEL_TITULO = 10f

        const val SEPARADOR_DE_CELDAS = "   "

        const val ABRE_PARENTESIS = '('.code
        const val CIERRA_PARENTESIS = ')'.code
        const val BARRA = '\\'.code

        /**
         * La codificación de las fuentes simples de este documento. Se declara aquí y se
         * usa aquí: cambiarla sin cambiar el `SIMPLE_ENCODING_LATIN` de arriba dejaría el
         * PDF escrito en una codificación y leído en otra.
         */
        val WIN_ANSI: Charset = Charset.forName("windows-1252")
    }
}
