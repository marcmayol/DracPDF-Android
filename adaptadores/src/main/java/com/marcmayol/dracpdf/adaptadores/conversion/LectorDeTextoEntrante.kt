package com.marcmayol.dracpdf.adaptadores.conversion

import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import com.marcmayol.dracpdf.dominio.puertos.TipoDeEntrada

/**
 * Lo que entra —texto, Markdown o HTML— entendido como títulos y párrafos.
 *
 * Devuelve el **mismo modelo** que produce la lectura de un PDF al convertirlo hacia
 * fuera, y eso no es casualidad: el escritorio aprendió que todas las conversiones
 * necesitan lo mismo —qué es un título y qué un párrafo— y sacó esa idea a un módulo
 * común. Aquí se entra por el otro extremo del mismo túnel, así que se reutiliza la
 * misma pieza en vez de inventar un segundo modelo que acabaría contradiciéndola.
 *
 * **Al entrar hay suerte y al salir no.** Un PDF no dice qué es un título y hay que
 * deducirlo de los tamaños de letra; un Markdown lo dice con almohadillas y un HTML con
 * `<h1>`. Aprovecharlo es gratis, y por eso estos tres formatos entran con su estructura
 * y no como una tira de texto plano.
 */
internal object LectorDeTextoEntrante {
    fun leer(
        contenido: String,
        tipo: TipoDeEntrada,
    ): DocumentoEstructurado =
        when (tipo) {
            TipoDeEntrada.MARKDOWN -> DocumentoEstructurado(deMarkdown(contenido))
            TipoDeEntrada.HTML -> DocumentoEstructurado(deMarkdown(comoMarcado(contenido)))
            else -> DocumentoEstructurado(deTextoLlano(contenido))
        }

    /**
     * Texto sin marcas: un párrafo por bloque separado con una línea en blanco.
     *
     * Las líneas de dentro de un párrafo se **vuelven a repartir**, no se respetan. Es
     * una decisión con precio: un `.txt` que fuera una tabla alineada a mano o un trozo
     * de código pierde su forma. A cambio, el caso normal —prosa cortada a ochenta
     * columnas porque así se escribió— se lee como prosa y no como una escalera de
     * renglones sueltos en una hoja el doble de ancha.
     */
    private fun deTextoLlano(contenido: String): List<BloqueDeTexto> =
        contenido
            .replace("\r\n", "\n")
            .split(Regex("\n[ \t]*\n"))
            .map { bloque -> bloque.replace('\n', ' ').trim() }
            .filter { it.isNotEmpty() }
            .map { BloqueDeTexto.Parrafo(it) }

    /**
     * Markdown, con lo que un documento de verdad usa: títulos, listas, citas, código
     * cercado y los adornos de dentro de la línea.
     *
     * No es un analizador de Markdown completo y no pretende serlo. Lo que hay aquí es lo
     * que sobrevive a un PDF: negritas y cursivas se pierden igualmente —el resultado va
     * etiquetado «reformateado»— así que sus marcas se quitan en vez de intentar honrarlas
     * a medias.
     */
    private fun deMarkdown(contenido: String): List<BloqueDeTexto> {
        val acumulado = MarkdownEnCurso()
        var dentroDeCodigo = false

        contenido.replace("\r\n", "\n").split('\n').forEach { linea ->
            val limpia = linea.trim()
            when {
                CERCA_DE_CODIGO.matches(limpia) -> {
                    acumulado.cerrarParrafo()
                    dentroDeCodigo = !dentroDeCodigo
                }

                // Dentro de un bloque cercado no hay marcas que interpretar: es texto tal
                // cual, y cada renglón es suyo. Reunirlos en un párrafo destruiría lo
                // único que ese bloque promete, que es conservar la forma.
                dentroDeCodigo -> if (linea.isNotBlank()) acumulado.anadirTalCual(linea)

                limpia.isEmpty() || SEPARADOR.matches(limpia) -> acumulado.cerrarParrafo()

                else -> acumulado.leerLinea(limpia)
            }
        }
        acumulado.cerrarParrafo()
        return acumulado.bloques
    }

    /**
     * Lo que se lleva escrito mientras se recorre el Markdown.
     *
     * Existe para que el recorrido quepa en la cabeza: con el acumulador y la decisión
     * de cada línea metidos en el mismo bucle, aquello eran cuatro niveles de anidación
     * y la parte que importaba —qué es un título y qué un párrafo— quedaba escondida al
     * fondo.
     */
    private class MarkdownEnCurso {
        val bloques = mutableListOf<BloqueDeTexto>()
        private val enCurso = StringBuilder()

        fun cerrarParrafo() {
            if (enCurso.isNotEmpty()) {
                bloques += BloqueDeTexto.Parrafo(enCurso.toString().trim())
                enCurso.setLength(0)
            }
        }

        fun anadirTalCual(linea: String) {
            bloques += BloqueDeTexto.Parrafo(linea)
        }

        /** Una línea normal: título, elemento de lista, o más texto del párrafo. */
        fun leerLinea(limpia: String) {
            val titulo = TITULO.matchEntire(limpia)
            val elemento = ELEMENTO_DE_LISTA.matchEntire(limpia)
            when {
                titulo != null -> {
                    cerrarParrafo()
                    bloques +=
                        BloqueDeTexto.Titulo(
                            texto = sinAdornos(titulo.groupValues[2]),
                            nivel = titulo.groupValues[1].length,
                        )
                }

                elemento != null -> {
                    cerrarParrafo()
                    bloques += BloqueDeTexto.Parrafo(VINETA + sinAdornos(elemento.groupValues[2]))
                }

                else -> {
                    if (enCurso.isNotEmpty()) enCurso.append(' ')
                    enCurso.append(sinAdornos(limpia.removePrefix(">").trim()))
                }
            }
        }
    }

    /**
     * HTML traducido a Markdown antes de leerlo.
     *
     * Es un rodeo aparente y en realidad es el camino corto: los dos formatos dicen las
     * mismas cosas —esto es un título de nivel dos, esto un elemento de lista— y con la
     * traducción hecha, la deducción de estructura ocurre **una sola vez**. Dos
     * analizadores separados serían dos sitios donde el nivel de un título puede salir
     * distinto.
     *
     * Lo que se tira se tira a conciencia: guiones de estilo y programas no son contenido,
     * y meter en el PDF el código JavaScript de una página sería un fallo, no una omisión.
     */
    private fun comoMarcado(html: String): String =
        html
            .replace(COMENTARIOS, "")
            .replace(PROGRAMAS_Y_ESTILOS, "")
            .replace(SALTO_DE_LINEA, "\n\n")
            .replace(APERTURA_DE_TITULO) { "\n\n${"#".repeat(it.groupValues[1].toInt())} " }
            .replace(APERTURA_DE_ELEMENTO, "\n\n- ")
            // Las celdas se separan con un espacio y no con un salto: dos celdas pegadas
            // dan «MadridBarcelona», que es peor que perder la rejilla de la tabla.
            .replace(CELDAS, " ")
            .replace(CIERRE_DE_BLOQUE, "\n\n")
            .replace(CUALQUIER_ETIQUETA, "")
            .let(::sinEntidades)
            // Las etiquetas se escriben con sangrías y saltos que no significan nada: sin
            // aplanarlos, cada indentación del fichero fuente sería un párrafo vacío.
            .split(Regex("\n[ \t]*\n"))
            .joinToString("\n\n") { it.replace(Regex("[ \t\n]+"), " ").trim() }

    /** Las marcas de dentro de la línea, que en un PDF no sobreviven de todos modos. */
    private fun sinAdornos(texto: String): String =
        texto
            .replace(IMAGEN_MARKDOWN, "$1")
            .replace(ENLACE_MARKDOWN, "$1")
            .replace(NEGRITA_O_CURSIVA, "$2")
            .replace(CODIGO_EN_LINEA, "$1")
            .trim()

    /**
     * Las entidades de HTML, las cinco del formato y las que un texto en castellano usa
     * de verdad.
     *
     * Las numéricas se resuelven todas —son una cuenta— y de las nombradas sólo están las
     * que aparecen: la lista completa son más de dos mil y ningún editor actual las
     * escribe, porque los ficheros van en UTF-8 desde hace veinte años.
     */
    private fun sinEntidades(texto: String): String =
        texto
            .replace(ENTIDAD_NUMERICA) { coincidencia ->
                val hexadecimal = coincidencia.groupValues[1].isNotEmpty()
                val digitos = coincidencia.groupValues[2]
                val codigo = digitos.toIntOrNull(if (hexadecimal) BASE_HEXADECIMAL else BASE_DECIMAL)
                codigo?.let { String(Character.toChars(it)) } ?: coincidencia.value
            }.replace(ENTIDAD_NOMBRADA) { coincidencia ->
                val nombre = coincidencia.groupValues[1]
                // Primero tal cual y luego en minúsculas: en HTML `&Aacute;` y `&aacute;`
                // son dos entidades distintas —«Á» y «á»— y unificarlas por comodidad
                // pondría todas las mayúsculas acentuadas en minúscula.
                NOMBRADAS[nombre] ?: NOMBRADAS[nombre.lowercase()] ?: coincidencia.value
            }

    private val CERCA_DE_CODIGO = Regex("^(```|~~~).*$")
    private val SEPARADOR = Regex("^([-*_])\\1{2,}$")
    private val TITULO = Regex("^(#{1,6})\\s+(.*)$")
    private val ELEMENTO_DE_LISTA = Regex("^([-*+]|\\d+[.)])\\s+(.*)$")

    private val IMAGEN_MARKDOWN = Regex("!\\[([^\\]]*)]\\([^)]*\\)")
    private val ENLACE_MARKDOWN = Regex("\\[([^\\]]*)]\\([^)]*\\)")
    private val NEGRITA_O_CURSIVA = Regex("(\\*{1,3}|_{1,3})(.+?)\\1")
    private val CODIGO_EN_LINEA = Regex("`([^`]*)`")

    private val COMENTARIOS = Regex("(?s)<!--.*?-->")
    private val PROGRAMAS_Y_ESTILOS = Regex("(?is)<(script|style)\\b.*?</\\1\\s*>")
    private val SALTO_DE_LINEA = Regex("(?i)<br\\s*/?>")
    private val APERTURA_DE_TITULO = Regex("(?i)<h([1-6])\\b[^>]*>")
    private val APERTURA_DE_ELEMENTO = Regex("(?i)<li\\b[^>]*>")
    private val CELDAS = Regex("(?i)</?(td|th)\\b[^>]*>")
    private val CIERRE_DE_BLOQUE =
        Regex("(?i)</?(p|div|section|article|header|footer|tr|h[1-6]|li|ul|ol|blockquote|pre|table)\\b[^>]*>")
    private val CUALQUIER_ETIQUETA = Regex("<[^>]*>")

    private val ENTIDAD_NUMERICA = Regex("&#(x?)([0-9a-fA-F]+);", RegexOption.IGNORE_CASE)
    private val ENTIDAD_NOMBRADA = Regex("&([a-zA-Z]+);")

    private val NOMBRADAS =
        mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "nbsp" to " ",
            "hellip" to "…",
            "mdash" to "—",
            "ndash" to "–",
            "laquo" to "«",
            "raquo" to "»",
            "ldquo" to "“",
            "rdquo" to "”",
            "iexcl" to "¡",
            "iquest" to "¿",
            "deg" to "°",
            "euro" to "€",
            "middot" to "·",
            "aacute" to "á",
            "eacute" to "é",
            "iacute" to "í",
            "oacute" to "ó",
            "uacute" to "ú",
            "Aacute" to "Á",
            "Eacute" to "É",
            "Iacute" to "Í",
            "Oacute" to "Ó",
            "Uacute" to "Ú",
            "ntilde" to "ñ",
            "Ntilde" to "Ñ",
            "uuml" to "ü",
            "Uuml" to "Ü",
            "ccedil" to "ç",
        )

    private const val VINETA = "• "
    private const val BASE_DECIMAL = 10
    private const val BASE_HEXADECIMAL = 16
}
