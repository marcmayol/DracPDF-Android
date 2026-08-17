package com.marcmayol.dracpdf.adaptadores.mupdf

import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Link
import com.artifex.mupdf.fitz.Outline
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.Point
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.SeekableStream
import com.artifex.mupdf.fitz.StructuredText
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentos
import com.marcmayol.dracpdf.dominio.modelo.DestinoEnlace
import com.marcmayol.dracpdf.dominio.modelo.EnlacePagina
import com.marcmayol.dracpdf.dominio.modelo.EntradaIndice
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.PropiedadesDocumento
import com.marcmayol.dracpdf.dominio.modelo.PuntoPt
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.modelo.SeleccionTexto
import com.marcmayol.dracpdf.dominio.puertos.EstructuraPdf
import com.marcmayol.dracpdf.dominio.puertos.TextoPdf

/**
 * El texto y la forma del documento, leídos con MuPDF.
 *
 * Todo pasa por el hilo del documento, como el resto de adaptadores del motor, y
 * **nada nativo sale de aquí**: las páginas y los textos estructurados se destruyen
 * antes de volver, y lo que se devuelve son datos del dominio. Un `StructuredText`
 * escapado se recogería en el hilo del recolector y se llevaría el proceso por
 * delante.
 */
class MuPdfContenido(
    private val sesiones: SesionesMuPdf,
    private val fuente: FuenteDocumentos,
) : TextoPdf,
    EstructuraPdf {
    override fun buscar(
        id: IdDocumento,
        pagina: Int,
        texto: String,
    ): List<RectPt> {
        if (texto.isBlank()) return emptyList()
        return enLaPagina(id, pagina) { hoja ->
            // Cada coincidencia llega partida en un trozo por línea. Se juntan en un
            // solo marco porque lo que el lector busca es «dónde está», y dos
            // rectángulos para una palabra partida por un guion se leerían como dos
            // resultados distintos.
            hoja.search(texto).orEmpty().mapNotNull { trozos -> marcoDe(trozos) }
        }
    }

    override fun palabraEn(
        id: IdDocumento,
        pagina: Int,
        punto: PuntoPt,
    ): SeleccionTexto? =
        enElTexto(id, pagina) { estructurado ->
            // `snapSelection` **modifica los puntos que recibe**: los mueve a los
            // bordes de la palabra que hay debajo. Por eso se le dan dos copias del
            // mismo punto y luego se leen de vuelta.
            val desde = Point(punto.x, punto.y)
            val hasta = Point(punto.x, punto.y)
            estructurado.snapSelection(desde, hasta, StructuredText.SELECT_WORDS)

            val texto = estructurado.copy(desde, hasta).orEmpty()
            val marcos = marcosDe(estructurado, desde, hasta)

            // El motor engancha la palabra **más cercana**, aunque el dedo esté en el
            // blanco de la hoja: tocar un margen devolvería la palabra de la otra
            // punta. Se exige que el punto caiga dentro de lo que se ha enganchado, y
            // así una zona sin texto no selecciona nada.
            if (texto.isBlank() || marcos.none { it.contiene(punto) }) null else SeleccionTexto(texto, marcos)
        }

    override fun seleccionEntre(
        id: IdDocumento,
        pagina: Int,
        desde: PuntoPt,
        hasta: PuntoPt,
    ): SeleccionTexto =
        enElTexto(id, pagina) { estructurado ->
            val a = Point(desde.x, desde.y)
            val b = Point(hasta.x, hasta.y)
            // Por caracteres y no por palabras: mientras se arrastra el asa, el usuario
            // está ajustando el borde exacto de lo que quiere.
            estructurado.snapSelection(a, b, StructuredText.SELECT_CHARS)
            SeleccionTexto(estructurado.copy(a, b).orEmpty(), marcosDe(estructurado, a, b))
        } ?: SeleccionTexto.NINGUNA

    override fun textoDe(
        id: IdDocumento,
        pagina: Int,
    ): String = enElTexto(id, pagina) { it.asText().orEmpty() } ?: ""

    override fun indice(id: IdDocumento): List<EntradaIndice> =
        sesiones.en(id) { documento ->
            val raiz = documento.loadOutline() ?: return@en emptyList()
            val entradas = mutableListOf<EntradaIndice>()
            aplanar(documento, raiz, nivel = 0, entradas)
            entradas
        }

    /**
     * Pasa el árbol del índice a una lista con sangría.
     *
     * Recursiva y sin límite de profundidad a propósito: un índice de un PDF viene de
     * su tabla de contenidos, y los que llegan a diez niveles son libros técnicos de
     * verdad, no documentos malformados.
     */
    private fun aplanar(
        documento: Document,
        entradas: Array<Outline>,
        nivel: Int,
        destino: MutableList<EntradaIndice>,
    ) {
        entradas.forEach { entrada ->
            destino += EntradaIndice(entrada.title.orEmpty(), paginaDe(documento, entrada.uri), nivel)
            entrada.down?.let { aplanar(documento, it, nivel + 1, destino) }
        }
    }

    override fun enlaces(
        id: IdDocumento,
        pagina: Int,
    ): List<EnlacePagina> =
        sesiones.en(id) { documento ->
            val hoja = documento.loadPage(pagina) ?: return@en emptyList()
            try {
                hoja.links.orEmpty().mapNotNull { enlace -> aEnlace(documento, enlace) }
            } finally {
                hoja.destroy()
            }
        }

    private fun aEnlace(
        documento: Document,
        enlace: Link,
    ): EnlacePagina? {
        val uri = enlace.uri
        if (uri.isNullOrEmpty()) return null
        val marco = aRect(enlace.bounds) ?: return null

        // Un enlace es externo si su URI trae esquema; si no, apunta a una página de
        // este mismo documento y hay que preguntarle al motor cuál es.
        val destino =
            if (Link.isExternal(uri)) {
                DestinoEnlace.Fuera(uri)
            } else {
                paginaDe(documento, uri)?.let { DestinoEnlace.Pagina(it) }
            } ?: return null

        return EnlacePagina(marco, destino)
    }

    override fun propiedades(id: IdDocumento): PropiedadesDocumento {
        val origen = sesiones.origenDe(id)
        val cifrado = sesiones.en(id) { it.getMetaData(Document.META_ENCRYPTION) }

        return sesiones.en(id) { documento ->
            PropiedadesDocumento(
                titulo = dato(documento, Document.META_INFO_TITLE),
                autor = dato(documento, Document.META_INFO_AUTHOR),
                asunto = dato(documento, Document.META_INFO_SUBJECT),
                palabrasClave = dato(documento, Document.META_INFO_KEYWORDS),
                creador = dato(documento, Document.META_INFO_CREATOR),
                productor = dato(documento, Document.META_INFO_PRODUCER),
                creado = dato(documento, Document.META_INFO_CREATIONDATE),
                modificado = dato(documento, Document.META_INFO_MODIFICATIONDATE),
                formato = dato(documento, Document.META_FORMAT),
                paginas = documento.countPages(),
                bytes = tamanoDe(origen),
                // «None» es lo que contesta un PDF sin cifrar; cualquier otra cosa
                // nombra el algoritmo con el que se cifró.
                cifrado = cifrado != null && !cifrado.equals(SIN_CIFRAR, ignoreCase = true),
            )
        }
    }

    /** Lo que ocupa el fichero. Se mide posicionándose al final, que es lo que hay. */
    private fun tamanoDe(origen: com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento): Long? =
        runCatching {
            fuente.abrir(origen).use { flujo -> flujo.seek(0, SeekableStream.SEEK_END) }
        }.getOrNull()

    private fun SeekableStream.use(bloque: (SeekableStream) -> Long): Long =
        try {
            bloque(this)
        } finally {
            (this as? AutoCloseable)?.let { runCatching { it.close() } }
        }

    private fun dato(
        documento: Document,
        clave: String,
    ): String? = documento.getMetaData(clave)?.takeIf { it.isNotBlank() }

    /** A qué página lleva un URI interno del motor, si lleva a alguna. */
    private fun paginaDe(
        documento: Document,
        uri: String?,
    ): Int? {
        if (uri.isNullOrEmpty() || Link.isExternal(uri)) return null
        return runCatching {
            documento.pageNumberFromLocation(documento.resolveLink(uri))
        }.getOrNull()?.takeIf { it >= 0 }
    }

    /** Hace algo con una página y la destruye, pase lo que pase. */
    private fun <T> enLaPagina(
        id: IdDocumento,
        pagina: Int,
        bloque: (Page) -> T,
    ): T =
        sesiones.en(id) { documento ->
            val hoja = documento.loadPage(pagina)
            try {
                bloque(hoja)
            } finally {
                hoja.destroy()
            }
        }

    /** Íd. con el texto estructurado, que es otro objeto nativo que hay que soltar. */
    private fun <T> enElTexto(
        id: IdDocumento,
        pagina: Int,
        bloque: (StructuredText) -> T?,
    ): T? =
        enLaPagina(id, pagina) { hoja ->
            val estructurado = hoja.toStructuredText()
            try {
                bloque(estructurado)
            } finally {
                estructurado.destroy()
            }
        }

    private fun marcosDe(
        estructurado: StructuredText,
        desde: Point,
        hasta: Point,
    ): List<RectPt> = estructurado.highlight(desde, hasta).orEmpty().mapNotNull { aRect(it.toRect()) }

    /** El marco que abarca todos los trozos de una coincidencia. */
    private fun marcoDe(trozos: Array<Quad>?): RectPt? {
        val rectangulos = trozos.orEmpty().mapNotNull { aRect(it.toRect()) }
        if (rectangulos.isEmpty()) return null
        return RectPt(
            x0 = rectangulos.minOf { it.x0 },
            y0 = rectangulos.minOf { it.y0 },
            x1 = rectangulos.maxOf { it.x1 },
            y1 = rectangulos.maxOf { it.y1 },
        )
    }

    /**
     * Si el punto cae dentro del marco, con un dedo de margen.
     *
     * El margen no es capricho: una línea de texto de 12 puntos mide cuatro
     * milímetros, y exigir el impacto exacto obligaría a apuntar con precisión de
     * ratón en una pantalla que se toca con el dedo.
     */
    private fun RectPt.contiene(punto: PuntoPt): Boolean =
        punto.x >= x0 - MARGEN_DEL_DEDO &&
            punto.x <= x1 + MARGEN_DEL_DEDO &&
            punto.y >= y0 - MARGEN_DEL_DEDO &&
            punto.y <= y1 + MARGEN_DEL_DEDO

    private fun aRect(rect: com.artifex.mupdf.fitz.Rect?): RectPt? {
        if (rect == null || rect.x1 <= rect.x0 || rect.y1 <= rect.y0) return null
        return RectPt(rect.x0, rect.y0, rect.x1, rect.y1)
    }

    private companion object {
        const val SIN_CIFRAR = "None"

        /** Puntos PDF de tolerancia alrededor de una palabra; unos dos milímetros. */
        const val MARGEN_DEL_DEDO = 6f
    }
}
