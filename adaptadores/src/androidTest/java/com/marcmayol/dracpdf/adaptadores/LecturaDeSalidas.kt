package com.marcmayol.dracpdf.adaptadores

import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Vuelve a leer lo que las conversiones escriben.
 *
 * Existe porque el criterio de la fase no se conforma con «el fichero se ha creado»: pide
 * que un ODT y un RTF **se relean** y devuelvan el texto esperado, y que el CSV y el XLSX
 * se comparen celda a celda. Un fichero de tamaño mayor que cero no demuestra nada; uno
 * mal formado también pesa.
 *
 * Se lee con lo que trae la plataforma —`java.util.zip` y el analizador XML del
 * sistema—, sin ninguna biblioteca de ofimática: si hiciera falta una para releer lo
 * escrito, la prueba estaría midiendo la biblioteca y no el escritor.
 */
object LecturaDeSalidas {
    /** Las entradas del ZIP en el orden en que están, con el método de cada una. */
    fun entradasDe(fichero: File): List<Pair<String, Int>> {
        val entradas = mutableListOf<Pair<String, Int>>()
        ZipInputStream(fichero.inputStream()).use { zip ->
            var entrada: ZipEntry? = zip.nextEntry
            while (entrada != null) {
                entradas += entrada.name to entrada.method
                entrada = zip.nextEntry
            }
        }
        return entradas
    }

    fun contenidoDe(
        fichero: File,
        nombre: String,
    ): String {
        ZipInputStream(fichero.inputStream()).use { zip ->
            var entrada: ZipEntry? = zip.nextEntry
            while (entrada != null) {
                if (entrada.name == nombre) return zip.readBytes().toString(Charsets.UTF_8)
                entrada = zip.nextEntry
            }
        }
        throw AssertionError("El paquete ${fichero.name} no tiene ninguna entrada «$nombre»")
    }

    /** Todo el texto de un ODT, ya sin etiquetas. */
    fun textoDeOdt(fichero: File): String = analizar(contenidoDe(fichero, "content.xml")).documentElement.textContent

    /** Los títulos de un ODT, con su nivel de esquema. */
    fun titulosDeOdt(fichero: File): List<Pair<String, Int>> =
        elementos(analizar(contenidoDe(fichero, "content.xml")), "text:h").map {
            it.textContent to it.getAttribute("text:outline-level").toInt()
        }

    /** Los párrafos de un ODT, sin contar los de dentro de las celdas de una tabla. */
    fun parrafosDeOdt(fichero: File): List<String> =
        elementos(analizar(contenidoDe(fichero, "content.xml")), "text:p")
            .filter { (it.parentNode as? Element)?.tagName != "table:table-cell" }
            .map { it.textContent }

    /** Las celdas de una hoja del XLSX, por filas y en orden. */
    fun celdasDeXlsx(
        fichero: File,
        hoja: Int,
    ): List<List<String>> {
        val documento = analizar(contenidoDe(fichero, "xl/worksheets/sheet$hoja.xml"))
        return elementos(documento, "row").map { fila ->
            elementos(fila, "c").map { celda -> elementos(celda, "t").firstOrNull()?.textContent.orEmpty() }
        }
    }

    /** Los nombres de las pestañas, en el orden del libro. */
    fun pestanasDeXlsx(fichero: File): List<String> =
        elementos(analizar(contenidoDe(fichero, "xl/workbook.xml")), "sheet").map { it.getAttribute("name") }

    /**
     * Un CSV releído. El fixture no tiene celdas entrecomilladas, así que basta con partir
     * por el separador: montar aquí un analizador de CSV sería probar el analizador.
     */
    fun celdasDeCsv(fichero: File): List<List<String>> =
        fichero
            .readText(Charsets.UTF_8)
            .removePrefix("")
            .split("\r\n")
            .filter { it.isNotEmpty() }
            .map { it.split(';') }

    /** Si el CSV empieza por la marca de orden de bytes que Excel necesita. */
    fun tieneMarcaDeOrden(fichero: File): Boolean = fichero.readText(Charsets.UTF_8).startsWith("")

    /**
     * El texto de un RTF, deshaciendo los comandos y los escapes `\uN?`.
     *
     * No es un analizador de RTF completo ni pretende serlo: reconoce lo que este escritor
     * produce, que es justo lo que hay que comprobar.
     */
    fun textoDeRtf(fichero: File): String {
        val rtf = fichero.readText(Charsets.US_ASCII)
        val salida = StringBuilder()
        var posicion = 0
        while (posicion < rtf.length) {
            posicion +=
                when (val caracter = rtf[posicion]) {
                    '\\' -> descifrarComando(rtf.substring(posicion + 1), salida)
                    '{', '}', '\n', '\r' -> 1
                    else -> {
                        salida.append(caracter)
                        1
                    }
                }
        }
        return salida.toString()
    }

    /** @return cuántos caracteres se ha comido, contando la barra. */
    private fun descifrarComando(
        resto: String,
        salida: StringBuilder,
    ): Int {
        UNICODE.find(resto)?.let { escape ->
            val codigo = escape.groupValues[1].toInt()
            salida.append(((codigo + VUELTA_DE_16_BITS) % VUELTA_DE_16_BITS).toChar())
            return 1 + escape.value.length
        }
        COMANDO.find(resto)?.let { comando ->
            if (comando.groupValues[1] in SEPARADORES) salida.append('\n')
            return 1 + comando.value.length
        }
        // Una barra delante de `\`, `{` o `}`: lo que va detrás es el carácter tal cual.
        salida.append(resto.first())
        return 2
    }

    private fun analizar(xml: String) =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(xml.byteInputStream(Charsets.UTF_8))

    private fun elementos(
        raiz: org.w3c.dom.Document,
        etiqueta: String,
    ): List<Element> = raiz.getElementsByTagName(etiqueta).comoLista()

    private fun elementos(
        raiz: Element,
        etiqueta: String,
    ): List<Element> = raiz.getElementsByTagName(etiqueta).comoLista()

    private fun org.w3c.dom.NodeList.comoLista(): List<Element> = (0 until length).mapNotNull { item(it) as? Element }

    private val UNICODE = Regex("""^u(-?\d+)\??""")
    private val COMANDO = Regex("""^([a-zA-Z]+)-?\d*[ ]?""")
    private val SEPARADORES = setOf("par", "line", "cell", "row", "page")
    private const val VUELTA_DE_16_BITS = 65536
}
