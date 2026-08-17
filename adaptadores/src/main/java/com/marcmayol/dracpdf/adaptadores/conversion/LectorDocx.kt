package com.marcmayol.dracpdf.adaptadores.conversion

import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.text.Normalizer
import java.util.zip.ZipInputStream

/**
 * Lee un `.docx` ajeno —de Word o de LibreOffice— y lo devuelve como contenido.
 *
 * Tres cosas hacen que esto no sea «leer un XML», y las tres salen a la primera con un
 * fichero de verdad:
 *
 * 1. **El XML viene en una sola línea y con los prefijos que a cada programa le apetece.**
 *    Nadie garantiza que el espacio de nombres de Word se llame `w:`; puede llamarse
 *    `wpml:` o declararse de nuevo a mitad del documento. Por eso se analiza con los
 *    espacios de nombres activados y se compara por URI, no por prefijo.
 * 2. **Word parte las palabras en varias «carreras» (`w:r`).** Un simple «Hola» puede
 *    llegar como cuatro `w:t` seguidos porque el corrector pasó por ahí o porque el
 *    usuario cambió de idioma en medio. La única lectura correcta es concatenar todos
 *    los `w:t` de un mismo `w:p`, que es justo lo que se hace aquí.
 * 3. **El nivel de un título no está en el párrafo.** El párrafo sólo dice qué estilo
 *    usa, y el identificador del estilo está traducido: «Heading1» en el Word inglés,
 *    «Ttulo1» en el español, «berschrift1» en el alemán. Se lee `word/styles.xml` para
 *    quedarse con el `w:outlineLvl` de cada estilo, que es un número igual en todos los
 *    idiomas, y sólo si falta se recurre a adivinar por el nombre.
 *
 * Lo que **no** se lee, y es a propósito: cabeceras y pies (viven en otras piezas del
 * paquete y no son el cuerpo del documento), imágenes, y todo lo que sea formato.
 */
class LectorDocx {
    /**
     * @param entrada el `.docx` completo. Se recorre **una sola vez** y en flujo: no se
     *   copia a disco, porque para leer un ZIP en secuencia no hace falta.
     * @throws IOException si dentro no hay un `word/document.xml`, que es lo que
     *   distingue un `.docx` de cualquier otro ZIP.
     */
    fun leer(entrada: InputStream): DocumentoEstructurado {
        var cuerpo: ByteArray? = null
        var estilos: ByteArray? = null
        ZipInputStream(entrada.buffered()).use { zip ->
            var pieza = zip.nextEntry
            while (pieza != null) {
                when (pieza.name.trimStart('/')) {
                    "word/document.xml" -> cuerpo = zip.readBytes()
                    "word/styles.xml" -> estilos = zip.readBytes()
                }
                zip.closeEntry()
                pieza = zip.nextEntry
            }
        }
        val documento = cuerpo ?: throw IOException("El fichero no es un .docx: no lleva word/document.xml")
        return DocumentoEstructurado(bloques(documento, nivelesPorEstilo(estilos)))
    }

    // -- El cuerpo ----------------------------------------------------------------

    private fun bloques(
        xml: ByteArray,
        niveles: Map<String, Int>,
    ): List<BloqueDeTexto> {
        val analizador = analizadorDe(xml)
        val bloques = mutableListOf<BloqueDeTexto>()
        // El .docx no tiene páginas: Word las recalcula al abrirlo. Lo único que se
        // sabe es cuántos saltos explícitos van vistos, y con eso se numera lo que el
        // modelo obliga a numerar.
        var pagina = 1
        while (analizador.next() != XmlPullParser.END_DOCUMENT) {
            if (analizador.eventType != XmlPullParser.START_TAG || !esDeWord(analizador)) continue
            when (analizador.name) {
                "p" -> {
                    val parrafo = leerParrafo(analizador, niveles)
                    parrafo.bloque?.let { bloques += it }
                    if (parrafo.saltaPagina) {
                        pagina++
                        bloques += BloqueDeTexto.SaltoDePagina(pagina)
                    }
                }

                // Las tablas se consumen enteras aquí dentro; así los párrafos de sus
                // celdas no vuelven a aparecer sueltos en el cuerpo.
                "tbl" -> leerTabla(analizador, pagina)?.let { bloques += it }
            }
        }
        return bloques
    }

    /** Lo que se saca de un `w:p`: el bloque que produce, si produce alguno. */
    private class Parrafo(
        val bloque: BloqueDeTexto?,
        val saltaPagina: Boolean,
    )

    /** Lo que se va sabiendo de un párrafo mientras se recorren sus carreras. */
    private class Acumulado {
        val texto = StringBuilder()
        var estilo: String? = null
        var nivel: Int? = null
        var salta = false
    }

    private fun leerParrafo(
        analizador: XmlPullParser,
        niveles: Map<String, Int>,
    ): Parrafo {
        val acumulado = Acumulado()
        dentroDe(analizador) { evento ->
            if (evento == XmlPullParser.START_TAG && esDeWord(analizador)) {
                anotarDelParrafo(analizador, acumulado)
            }
        }

        val limpio = acumulado.texto.toString().trim()
        // Los párrafos en blanco no son contenido: Word los usa para separar y hay a
        // docenas. Colarlos daría un PDF lleno de huecos y un modelo lleno de ruido.
        if (limpio.isEmpty()) return Parrafo(bloque = null, saltaPagina = acumulado.salta)

        val nivel = acumulado.nivel ?: acumulado.estilo?.let { niveles[it] ?: nivelPorNombre(it) }
        val bloque =
            if (nivel == null) {
                BloqueDeTexto.Parrafo(limpio)
            } else {
                BloqueDeTexto.Titulo(limpio, nivel.coerceIn(1, NIVELES_DE_TITULO))
            }
        return Parrafo(bloque, acumulado.salta)
    }

    private fun anotarDelParrafo(
        analizador: XmlPullParser,
        acumulado: Acumulado,
    ) {
        when (analizador.name) {
            "pStyle" -> acumulado.estilo = atributo(analizador, "val")
            "outlineLvl" -> acumulado.nivel = atributo(analizador, "val")?.toIntOrNull()?.plus(1)
            "br" ->
                if (atributo(analizador, "type") == "page") {
                    acumulado.salta = true
                } else {
                    acumulado.texto.append('\n')
                }

            "tab" -> acumulado.texto.append('\t')
            // Sólo `w:t`. `w:delText` es texto **borrado** con control de cambios y
            // resucitarlo sería meter en el PDF algo que el autor quitó.
            "t" -> acumulado.texto.append(textoDe(analizador))
        }
    }

    /**
     * Una tabla, fila a fila.
     *
     * Se marca **aproximada** siempre, y no porque se dude de la lectura —el `.docx`
     * dice dónde empieza y acaba cada celda— sino porque lo que se pierde es real: las
     * celdas combinadas se leen como celdas normales, y una tabla dentro de otra acaba
     * aplanada dentro de su celda. Prometer una tabla exacta sería mentir sobre eso.
     */
    private fun leerTabla(
        analizador: XmlPullParser,
        pagina: Int,
    ): BloqueDeTexto.Tabla? {
        val filas = mutableListOf<MutableList<String>>()
        dentroDe(analizador) { evento ->
            if (evento == XmlPullParser.START_TAG && esDeWord(analizador)) {
                when (analizador.name) {
                    "tr" -> filas += mutableListOf<String>()
                    "tc" -> filas.lastOrNull()?.add(textoDeCelda(analizador))
                }
            }
        }
        val utiles = filas.filter { fila -> fila.isNotEmpty() }
        return if (utiles.isEmpty()) null else BloqueDeTexto.Tabla(utiles, pagina, aproximada = true)
    }

    /** Todo el texto de un `w:tc`, con un salto de línea entre sus párrafos. */
    private fun textoDeCelda(analizador: XmlPullParser): String {
        val texto = StringBuilder()
        dentroDe(analizador) { evento ->
            if (esDeWord(analizador)) anotarDeLaCelda(analizador, evento, texto)
        }
        return texto.toString().trim()
    }

    private fun anotarDeLaCelda(
        analizador: XmlPullParser,
        evento: Int,
        texto: StringBuilder,
    ) {
        if (evento == XmlPullParser.START_TAG) {
            when (analizador.name) {
                "t" -> texto.append(textoDe(analizador))
                "tab" -> texto.append('\t')
                "br" -> texto.append('\n')
            }
        } else if (evento == XmlPullParser.END_TAG && analizador.name == "p" && texto.isNotEmpty()) {
            texto.append('\n')
        }
    }

    /**
     * El texto de un elemento, hasta su cierre.
     *
     * No vale `nextText()`: entrega el contenido de una vez y se atraganta si dentro
     * hay cualquier otra etiqueta. Un analizador puede además partir un texto largo en
     * varios eventos, y esto los junta todos.
     */
    private fun textoDe(analizador: XmlPullParser): String {
        val texto = StringBuilder()
        dentroDe(analizador) { evento ->
            if (evento in EVENTOS_CON_TEXTO) texto.append(analizador.text.orEmpty())
        }
        return texto.toString()
    }

    /**
     * Recorre lo que hay dentro del elemento en el que está parado el analizador y le
     * pasa cada evento a [visita], hasta el cierre de ese elemento.
     *
     * El final se reconoce por la **profundidad**, no contando etiquetas: un `w:p` puede
     * llevar dentro otros `w:p` —dentro de un cuadro de texto, por ejemplo— y quien
     * cuenta aperturas y cierres por nombre se sale del elemento equivocado. Además, un
     * [visita] puede consumir un subárbol entero (es lo que hace la lectura de una
     * celda) y esto sigue funcionando, porque al volver la profundidad vuelve con él.
     */
    private fun dentroDe(
        analizador: XmlPullParser,
        visita: (Int) -> Unit,
    ) {
        val fondo = analizador.depth
        var evento = analizador.next()
        while (evento != XmlPullParser.END_DOCUMENT &&
            !(evento == XmlPullParser.END_TAG && analizador.depth == fondo)
        ) {
            visita(evento)
            evento = analizador.next()
        }
    }

    // -- Los estilos ---------------------------------------------------------------

    /**
     * De identificador de estilo a nivel de título, leyendo `word/styles.xml`.
     *
     * El `w:outlineLvl` manda sobre el nombre: es un número, no una palabra traducida.
     */
    private fun nivelesPorEstilo(xml: ByteArray?): Map<String, Int> {
        if (xml == null) return emptyMap()
        val niveles = mutableMapOf<String, Int>()
        val analizador = analizadorDe(xml)
        var identificador: String? = null
        var nombre: String? = null
        var contorno: Int? = null

        while (analizador.next() != XmlPullParser.END_DOCUMENT) {
            if (!esDeWord(analizador)) continue
            val etiqueta = analizador.name
            if (analizador.eventType == XmlPullParser.START_TAG) {
                when {
                    etiqueta == "style" -> {
                        identificador = atributo(analizador, "styleId")
                        nombre = null
                        contorno = null
                    }
                    // Fuera de un `w:style` no hay nada que anotar: el mismo nombre de
                    // etiqueta aparece en sitios que no son estilos.
                    identificador == null -> Unit
                    etiqueta == "name" -> nombre = atributo(analizador, "val")
                    etiqueta == "outlineLvl" -> contorno = atributo(analizador, "val")?.toIntOrNull()
                }
            } else if (analizador.eventType == XmlPullParser.END_TAG && etiqueta == "style") {
                anotarNivel(niveles, identificador, contorno, nombre)
                identificador = null
            }
        }
        return niveles
    }

    private fun anotarNivel(
        niveles: MutableMap<String, Int>,
        identificador: String?,
        contorno: Int?,
        nombre: String?,
    ) {
        if (identificador == null) return
        val nivel = contorno?.plus(1) ?: nivelPorNombre(nombre ?: identificador) ?: return
        niveles[identificador] = nivel.coerceIn(1, NIVELES_DE_TITULO)
    }

    /**
     * Adivinar el nivel por el nombre del estilo, que es el último recurso.
     *
     * Se quitan acentos, espacios y mayúsculas antes de mirar, porque el mismo estilo
     * llega escrito de todas las maneras: «heading 1», «Heading1», «Título 1» y el
     * «Ttulo1» que produce el Word español, que se come la í al fabricar el
     * identificador. La lista de palabras cubre los idiomas en los que a él le pueden
     * llegar documentos; para el resto está el `w:outlineLvl`, que no depende de esto.
     */
    private fun nivelPorNombre(nombre: String): Int? {
        val llano =
            Normalizer
                .normalize(nombre, Normalizer.Form.NFD)
                .replace(SIN_DIACRITICOS, "")
                .lowercase()
                .filter { it.isLetterOrDigit() }
        val coincidencia = NOMBRE_DE_TITULO.matchEntire(llano) ?: return null
        return coincidencia.groupValues[2].toIntOrNull()
    }

    // -- Herramientas del analizador -----------------------------------------------

    private fun analizadorDe(xml: ByteArray): XmlPullParser {
        val fabrica = XmlPullParserFactory.newInstance()
        fabrica.isNamespaceAware = true
        return fabrica.newPullParser().apply {
            // Sin codificación declarada: la saca del prólogo del propio XML, que es
            // donde Word y LibreOffice la escriben.
            setInput(ByteArrayInputStream(xml), null)
        }
    }

    /**
     * Si la etiqueta es del vocabulario de Word.
     *
     * Se aceptan las dos URI —la transicional de 2006, que es la que usa todo el mundo,
     * y la «strict» de ECMA— y también la vacía, para el caso del XML escrito a mano sin
     * declarar nada. Lo que no se mira nunca es el prefijo.
     */
    private fun esDeWord(analizador: XmlPullParser): Boolean {
        // Fuera de una etiqueta no hay espacio de nombres que valga: en un texto suelto
        // el analizador devuelve nulo, y preguntarle es de quien no distingue eventos.
        val espacio = analizador.namespace ?: return false
        return espacio.isEmpty() || espacio in ESPACIOS_DE_WORD
    }

    /**
     * Un atributo por su nombre local, venga con el prefijo de Word o sin ninguno.
     *
     * `w:val` es lo normal, pero el atributo sin prefijo aparece en documentos escritos
     * a mano y en algún generador descuidado, y no cuesta nada aceptarlo.
     */
    private fun atributo(
        analizador: XmlPullParser,
        nombre: String,
    ): String? {
        ESPACIOS_DE_WORD.forEach { espacio ->
            analizador.getAttributeValue(espacio, nombre)?.let { return it }
        }
        return analizador.getAttributeValue(null, nombre)
    }

    private companion object {
        const val NIVELES_DE_TITULO = 6

        /** Lo que en un analizador de flujo cuenta como letras y no como estructura. */
        val EVENTOS_CON_TEXTO =
            setOf(XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF)

        val ESPACIOS_DE_WORD =
            listOf(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
                "http://purl.oclc.org/ooxml/wordprocessingml/main",
            )

        val SIN_DIACRITICOS = "\\p{Mn}+".toRegex()

        /** «heading1», «titulo1», «ttulo1», «berschrift1»… y el número al final. */
        val NOMBRE_DE_TITULO =
            (
                "(heading|title|titulo|ttulo|titol|ttol|titre|titolo|" +
                    "uberschrift|berschrift|kop|rubrik|nagowek|zaglowek)([1-9])"
            ).toRegex()
    }
}
