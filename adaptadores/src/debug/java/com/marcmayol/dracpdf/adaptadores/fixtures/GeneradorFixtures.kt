package com.marcmayol.dracpdf.adaptadores.fixtures

import com.artifex.mupdf.fitz.Buffer
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Font
import com.artifex.mupdf.fitz.LinkDestination
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.Rect
import java.io.File

/**
 * Fabrica los PDF de prueba. La regla de la casa dice que los fixtures se generan
 * por script y nunca se versionan como binarios; aquí el «script» es esto, y usa el
 * propio MuPDF para escribirlos.
 *
 * Que el motor que escribe el fixture sea el mismo que lo va a leer tiene una
 * ventaja concreta: un fallo del test no puede venir nunca de un PDF mal generado
 * por otra herramienta. Y evita que este repositorio dependa del entorno Python del
 * DracPDF de escritorio para poder probarse.
 */
object GeneradorFixtures {
    private const val ANCHO_A4 = 595f
    private const val ALTO_A4 = 842f

    /**
     * Un PDF con [paginas] páginas, cada una con su número escrito en grande y un
     * párrafo debajo, para que haya algo que rasterizar y algo que buscar.
     */
    fun documento(
        destino: File,
        paginas: Int,
        contrasena: String? = null,
    ): File {
        val pdf = PDFDocument()
        try {
            val fuente = pdf.addSimpleFont(Font("Helvetica"), Font.SIMPLE_ENCODING_LATIN)
            val recursos =
                pdf.newDictionary().apply {
                    put("Font", pdf.newDictionary().apply { put("F1", fuente) })
                }
            val caja = Rect(0f, 0f, ANCHO_A4, ALTO_A4)

            repeat(paginas) { indice ->
                val hoja = pdf.addPage(caja, 0, recursos, contenidoDe(indice + 1, paginas))
                pdf.insertPage(-1, hoja)
            }

            destino.parentFile?.mkdirs()
            pdf.save(destino.absolutePath, opcionesDe(contrasena))
        } finally {
            pdf.destroy()
        }
        return destino
    }

    private fun opcionesDe(contrasena: String?): String =
        if (contrasena == null) {
            ""
        } else {
            "encrypt=aes-256,user-password=$contrasena,owner-password=$contrasena"
        }

    /**
     * El contenido de una página, en el lenguaje de dibujo del PDF: el número de
     * página en 48 puntos y una línea de texto debajo.
     */
    private fun contenidoDe(
        numero: Int,
        total: Int,
    ): String =
        buildString {
            append("BT /F1 48 Tf 72 720 Td (Pagina $numero de $total) Tj ET\n")
            // ASCII a secas. El flujo se escribe en UTF-8 y la fuente base lo lee con la
            // codificación WinAnsi, así que un «·» llegaba a la página como «Â·» y salía
            // en todas las capturas. No vale la pena codificar bien por un separador.
            append("BT /F1 12 Tf 72 660 Td (DracPDF Android - fixture de la Fase 1) Tj ET\n")
            // Un marco, para que el render tenga tinta hasta los bordes y se note si
            // la escala se aplica mal.
            append("2 w 36 36 ${ANCHO_A4 - 72} ${ALTO_A4 - 72} re S\n")
        }

    /**
     * Un PDF con una **imagen de verdad** a toda página en cada hoja, guardado sin
     * comprimir.
     *
     * Existe para la compresión: un documento de puro texto encoge por deduplicación y
     * no demuestra que apretar imágenes haga nada.
     *
     * El dibujo es un degradado, **no ruido**: el ruido aleatorio es incompresible por
     * definición, así que con él el fichero no encoge —crece unos bytes— y la prueba no
     * demostraría nada. Un degradado guardado en crudo es justo el caso que la
     * herramienta tiene que mejorar: la foto que nadie optimizó al meterla en el PDF.
     */
    fun conImagenes(
        destino: File,
        paginas: Int,
    ): File {
        val pdf = PDFDocument()
        try {
            val caja = Rect(0f, 0f, ANCHO_A4, ALTO_A4)
            val imagen =
                pdf.addRawStream(
                    Buffer().also { it.writeBytes(pixelesDeDegradado()) },
                    diccionarioDeImagen(pdf),
                )
            val recursos =
                pdf.newDictionary().apply {
                    put("XObject", pdf.newDictionary().apply { put("Im1", imagen) })
                }

            repeat(paginas) {
                // La imagen se estira a la caja de la página: la matriz lleva el ancho y
                // el alto, y el resto es dibujarla.
                val hoja = pdf.addPage(caja, 0, recursos, "q $ANCHO_A4 0 0 $ALTO_A4 0 0 cm /Im1 Do Q")
                pdf.insertPage(-1, hoja)
            }

            destino.parentFile?.mkdirs()
            // Sin `compress`: el fixture tiene que llegar gordo para que comprimirlo se
            // note. Guardarlo ya apretado mediría la compresión contra sí misma.
            pdf.save(destino.absolutePath, "")
        } finally {
            pdf.destroy()
        }
        return destino
    }

    /**
     * Un PDF con **índice y enlaces**: lo que hace falta para probar la navegación.
     *
     * El índice se escribe con el iterador del motor y los enlaces con
     * `createLink`, en vez de armar los diccionarios a mano: así el fixture se parece a
     * lo que produce cualquier generador serio y no a lo que este proyecto entiende por
     * un índice.
     *
     * Trae los dos tipos de enlace que hay que distinguir: uno que lleva a otra página
     * del documento y otro que se va fuera.
     */
    fun conIndiceYEnlaces(
        destino: File,
        paginas: Int = PAGINAS_CON_INDICE,
    ): File {
        documento(destino, paginas)
        val pdf = Document.openDocument(destino.absolutePath) as PDFDocument
        try {
            enlazarPrimeraPagina(pdf, paginas)
            escribirIndice(pdf, paginas)
            // A un fichero distinto y de vuelta: guardar encima del que está abierto
            // deja el PDF a medias, y aquí interesa el resultado, no la maniobra.
            val temporal = File(destino.parentFile, "tmp-${destino.name}")
            pdf.save(temporal.absolutePath, "")
            pdf.destroy()
            temporal.copyTo(destino, overwrite = true)
            temporal.delete()
            return destino
        } catch (e: RuntimeException) {
            pdf.destroy()
            throw e
        }
    }

    /** Un enlace a la última página y otro a la web, los dos en la primera hoja. */
    private fun enlazarPrimeraPagina(
        pdf: PDFDocument,
        paginas: Int,
    ) {
        val primera = pdf.loadPage(0)
        try {
            primera.createLink(
                Rect(MARGEN_ENLACE, ALTO_A4 - ALTO_ENLACE, MARGEN_ENLACE + ANCHO_ENLACE, ALTO_A4 - MARGEN_ENLACE),
                pdf.formatLinkURI(LinkDestination.Fit(0, paginas - 1)),
            )
            primera.createLink(
                Rect(MARGEN_ENLACE, MARGEN_ENLACE, MARGEN_ENLACE + ANCHO_ENLACE, MARGEN_ENLACE + ALTO_ENLACE),
                WEB_DE_PRUEBA,
            )
        } finally {
            primera.destroy()
        }
    }

    /** Una entrada por página, todas al mismo nivel: «Capítulo 1», «Capítulo 2»… */
    private fun escribirIndice(
        pdf: PDFDocument,
        paginas: Int,
    ) {
        val iterador = pdf.outlineIterator()
        try {
            repeat(paginas) { indice ->
                iterador.insert(
                    "Capítulo ${indice + 1}",
                    pdf.formatLinkURI(LinkDestination.Fit(0, indice)),
                    false,
                    0f,
                    0f,
                    0f,
                    0,
                )
                // `insert` deja el cursor en la entrada nueva; hay que avanzar para que
                // la siguiente vaya detrás y no delante.
                iterador.next()
            }
        } finally {
            iterador.destroy()
        }
    }

    /**
     * Un PDF con **un título, un párrafo y una tabla conocida**, para las conversiones.
     *
     * La tabla se dibuja como la dibuja cualquier generador de informes: texto colocado en
     * unas coordenadas, sin rejilla y sin nada que diga «esto es una tabla», porque eso es
     * justo lo que un PDF no guarda. Que la detección tenga que adivinarla a partir de las
     * posiciones no es una limitación del fixture: es el problema de verdad.
     *
     * Todo en ASCII a propósito: el fixture se escribe con la codificación latina simple
     * de Helvetica, y meter acentos aquí probaría la codificación del generador en vez de
     * la conversión. Los acentos y el escapado se prueban aparte, con estructuras
     * inventadas que no pasan por ningún PDF.
     */
    fun conTabla(destino: File): File {
        val pdf = PDFDocument()
        try {
            val fuente = pdf.addSimpleFont(Font("Helvetica"), Font.SIMPLE_ENCODING_LATIN)
            val recursos =
                pdf.newDictionary().apply {
                    put("Font", pdf.newDictionary().apply { put("F1", fuente) })
                }
            val hoja = pdf.addPage(Rect(0f, 0f, ANCHO_A4, ALTO_A4), 0, recursos, contenidoConTabla())
            pdf.insertPage(-1, hoja)
            destino.parentFile?.mkdirs()
            pdf.save(destino.absolutePath, "")
        } finally {
            pdf.destroy()
        }
        return destino
    }

    /** Lo que dice la tabla de [conTabla], celda a celda, para comparar contra ello. */
    val TABLA_ESPERADA: List<List<String>> =
        listOf(
            listOf("Producto", "Unidades", "Precio"),
            listOf("Tinta", "12", "3,50"),
            listOf("Papel", "500", "0,02"),
            listOf("Grapas", "40", "1,10"),
        )

    /** El título de [conTabla], que la conversión tiene que reconocer como tal. */
    const val TITULO_CON_TABLA = "Informe de existencias"

    /** El párrafo de [conTabla], que no es un título por mucho que vaya solo. */
    const val PARRAFO_CON_TABLA = "Resumen del almacen en la fecha indicada."

    /**
     * El título en 24 puntos, el párrafo en 11 y la tabla en 11, con las columnas bien
     * separadas y las filas seguidas: las tres cosas que la deducción mira.
     */
    private fun contenidoConTabla(): String =
        buildString {
            append("BT /F1 $CUERPO_DE_TITULO Tf $COLUMNAS_TABLA_PRIMERA $ALTURA_TITULO Td ($TITULO_CON_TABLA) Tj ET\n")
            append("BT /F1 $CUERPO_NORMAL Tf $COLUMNAS_TABLA_PRIMERA $ALTURA_PARRAFO Td ($PARRAFO_CON_TABLA) Tj ET\n")
            TABLA_ESPERADA.forEachIndexed { fila, celdas ->
                val altura = ALTURA_PRIMERA_FILA - fila * SEPARACION_DE_FILAS
                celdas.forEachIndexed { columna, celda ->
                    val x = COLUMNAS_TABLA_PRIMERA + columna * SEPARACION_DE_COLUMNAS
                    append("BT /F1 $CUERPO_NORMAL Tf $x $altura Td ($celda) Tj ET\n")
                }
            }
        }

    private const val CUERPO_DE_TITULO = 24
    private const val CUERPO_NORMAL = 11
    private const val ALTURA_TITULO = 770
    private const val ALTURA_PARRAFO = 720
    private const val ALTURA_PRIMERA_FILA = 640
    private const val SEPARACION_DE_FILAS = 25
    private const val COLUMNAS_TABLA_PRIMERA = 72
    private const val SEPARACION_DE_COLUMNAS = 160

    private fun diccionarioDeImagen(pdf: PDFDocument) =
        pdf.newDictionary().apply {
            put("Type", pdf.newName("XObject"))
            put("Subtype", pdf.newName("Image"))
            put("Width", pdf.newInteger(LADO_IMAGEN))
            put("Height", pdf.newInteger(LADO_IMAGEN))
            put("ColorSpace", pdf.newName("DeviceRGB"))
            put("BitsPerComponent", pdf.newInteger(BITS_POR_COMPONENTE))
        }

    /** Un degradado en color, que es una imagen de verdad y sí se deja comprimir. */
    private fun pixelesDeDegradado(): ByteArray {
        val pixeles = ByteArray(LADO_IMAGEN * LADO_IMAGEN * CANALES)
        var i = 0
        for (y in 0 until LADO_IMAGEN) {
            for (x in 0 until LADO_IMAGEN) {
                pixeles[i++] = (x * MAXIMO_CANAL / LADO_IMAGEN).toByte()
                pixeles[i++] = (y * MAXIMO_CANAL / LADO_IMAGEN).toByte()
                pixeles[i++] = ((x + y) * MAXIMO_CANAL / (2 * LADO_IMAGEN)).toByte()
            }
        }
        return pixeles
    }

    private const val LADO_IMAGEN = 320
    private const val CANALES = 3
    private const val BITS_POR_COMPONENTE = 8
    private const val MAXIMO_CANAL = 255

    /** Suficientes para que «ir a la última» sea un salto de verdad. */
    const val PAGINAS_CON_INDICE = 5

    /** La dirección del enlace externo del fixture. No se visita en ningún test. */
    const val WEB_DE_PRUEBA = "https://marcmayol.com/dracpdf"

    private const val MARGEN_ENLACE = 72f
    private const val ANCHO_ENLACE = 200f
    private const val ALTO_ENLACE = 40f
}
