package com.marcmayol.dracpdf.adaptadores.conversion

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.marcmayol.dracpdf.adaptadores.saf.SalidasDeHerramienta
import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.ComponedorDePdf
import com.marcmayol.dracpdf.dominio.puertos.Progreso
import java.io.File

/**
 * Compone un PDF a partir de contenido sin páginas, con el PDF que Android trae de
 * fábrica (`android.graphics.pdf.PdfDocument`) y no con MuPDF.
 *
 * **Por qué no MuPDF, que es el motor de la casa.** MuPDF sabe escribir un PDF, pero por
 * la vía de `addPage` hay que entregarle el contenido ya escrito en el lenguaje de
 * dibujo del formato, como hace el generador de fixtures. Eso obliga a dos cosas que
 * aquí son justo lo que se promete y no se puede improvisar:
 *
 * - **Los acentos y las eñes.** Un `Tj` con una fuente base-14 escribe los bytes tal
 *   cual, así que «Añó» exige codificar a mano en la codificación de la fuente y
 *   escapar los paréntesis y las barras. Un solo carácter fuera de la tabla sale como
 *   otro distinto, y el usuario no ve un error: ve un texto mal escrito.
 * - **Partir en páginas.** Para saber dónde se acaba una línea hay que medir el texto, y
 *   por esa vía no hay métricas: habría que estimar el ancho de cada letra, que es
 *   adivinar. `Paint.breakText` mide con la fuente de verdad, y por eso el corte cae
 *   donde tiene que caer.
 *
 * El PDF que sale de aquí lleva **texto de verdad**, no dibujos de letras: el motor de
 * Android incrusta la fuente y su tabla a Unicode, así que el resultado se busca y se
 * copia. El test lo comprueba releyendo con MuPDF lo que escribió Android.
 *
 * Lo que no hace: columnas, imágenes, notas al pie ni nada que no sea texto corrido y
 * tablas de rejilla. El contenido de origen tampoco lo tiene.
 */
class ComponedorPdfAndroid(
    private val salidas: SalidasDeHerramienta,
    private val carpetaTemporal: File,
) : ComponedorDePdf {
    override fun componer(
        documento: DocumentoEstructurado,
        destino: OrigenDocumento,
        progreso: Progreso,
    ): Int {
        val pdf = PdfDocument()
        val maqueta = Maqueta(pdf)
        try {
            val total = documento.bloques.size
            documento.bloques.forEachIndexed { indice, bloque ->
                maqueta.colocar(bloque)
                if (!progreso.paso(indice + 1, total)) {
                    // Cancelar deja el destino sin tocar: el PDF entero vive en memoria
                    // hasta el volcado final. Lo que sí hay que hacer es cerrar la hoja
                    // que estuviera abierta, porque el motor se niega a soltar un
                    // documento con una página a medias.
                    maqueta.abandonar()
                    return 0
                }
            }
            maqueta.cerrar()
            escribir(pdf, destino)
            return maqueta.paginas
        } finally {
            pdf.close()
        }
    }

    private fun escribir(
        pdf: PdfDocument,
        destino: OrigenDocumento,
    ) {
        carpetaTemporal.mkdirs()
        val temporal = File.createTempFile("compuesto-", ".pdf", carpetaTemporal)
        try {
            temporal.outputStream().use(pdf::writeTo)
            salidas.volcar(temporal, destino)
        } finally {
            temporal.delete()
        }
    }
}

/**
 * El cursor que va bajando por la hoja.
 *
 * Existe como clase y no como un puñado de funciones porque todo lo que hace depende de
 * dos cosas que cambian a cada línea —qué página está abierta y por qué altura va— y
 * pasarlas de función en función convertía cada firma en un recado.
 */
private class Maqueta(
    private val pdf: PdfDocument,
) {
    private val cuerpo = pincel(CUERPO_PT, negrita = false)
    private val borde =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = GROSOR_LINEA
        }

    private var pagina: PdfDocument.Page? = null
    private var lienzo: Canvas? = null
    private var altura = MARGEN

    var paginas = 0
        private set

    fun colocar(bloque: BloqueDeTexto) {
        when (bloque) {
            is BloqueDeTexto.Titulo -> titulo(bloque)
            is BloqueDeTexto.Parrafo -> parrafo(bloque.texto, cuerpo, ESPACIO_ENTRE_PARRAFOS)
            is BloqueDeTexto.Tabla -> tabla(bloque)
            is BloqueDeTexto.SaltoDePagina -> cerrarPagina()
        }
    }

    /** Cierra lo que quede abierto. Un PDF sin una sola página no lo abre nadie. */
    fun cerrar() {
        if (paginas == 0) abrirPagina()
        cerrarPagina()
    }

    /** Suelta la hoja a medias sin rematar el documento: para cuando se cancela. */
    fun abandonar() = cerrarPagina()

    private fun titulo(titulo: BloqueDeTexto.Titulo) {
        val nivel = titulo.nivel.coerceIn(1, TAMANOS_DE_TITULO.size)
        val pincel = pincel(TAMANOS_DE_TITULO[nivel - 1], negrita = true)
        // El aire de arriba sólo se pone si el título no estrena página: una hoja que
        // empieza con un hueco parece un error de maquetación.
        if (altura > MARGEN) altura += ESPACIO_ANTES_DE_TITULO
        parrafo(titulo.texto, pincel, ESPACIO_ENTRE_PARRAFOS)
    }

    private fun parrafo(
        texto: String,
        pincel: Paint,
        aireDespues: Float,
    ) {
        val alto = alturaDeLinea(pincel)
        repartir(texto, pincel, ANCHO_UTIL).forEach { linea ->
            val dibujo = hueco(alto)
            dibujo.drawText(linea, MARGEN, altura - pincel.fontMetrics.ascent, pincel)
            altura += alto
        }
        altura += aireDespues
    }

    /**
     * Una tabla de rejilla, con todas las columnas del mismo ancho.
     *
     * Igualarlas es una decisión, no una limitación técnica: el contenido de origen no
     * trae anchos —ni el `.docx` que se lee ni la deducción sobre un PDF— y repartir a
     * ojo según lo que ocupa cada celda daría tablas distintas cada vez.
     */
    private fun tabla(tabla: BloqueDeTexto.Tabla) {
        val columnas = tabla.columnas
        if (columnas == 0) return
        val anchoColumna = ANCHO_UTIL / columnas
        val anchoTexto = anchoColumna - 2 * RELLENO_DE_CELDA
        val alto = alturaDeLinea(cuerpo)

        tabla.filas.forEach { fila ->
            val celdas = (0 until columnas).map { repartir(fila.getOrElse(it) { "" }, cuerpo, anchoTexto) }
            val altoFila = (celdas.maxOf { it.size } * alto) + 2 * RELLENO_DE_CELDA
            // La fila entera cambia de hoja o no cambia: partirla por la mitad dejaría
            // media rejilla arriba y media abajo, que se lee peor que el hueco.
            val dibujo = hueco(altoFila)
            dibujarFila(dibujo, celdas, anchoColumna, altoFila, alto)
            altura += altoFila
        }
        altura += ESPACIO_ENTRE_PARRAFOS
    }

    private fun dibujarFila(
        dibujo: Canvas,
        celdas: List<List<String>>,
        anchoColumna: Float,
        altoFila: Float,
        altoLinea: Float,
    ) {
        celdas.forEachIndexed { columna, lineas ->
            val izquierda = MARGEN + columna * anchoColumna
            dibujo.drawRect(izquierda, altura, izquierda + anchoColumna, altura + altoFila, borde)
            lineas.forEachIndexed { fila, linea ->
                val base = altura + RELLENO_DE_CELDA + fila * altoLinea - cuerpo.fontMetrics.ascent
                dibujo.drawText(linea, izquierda + RELLENO_DE_CELDA, base, cuerpo)
            }
        }
    }

    /** Asegura que caben [alto] puntos, abriendo hoja nueva si hace falta. */
    private fun hueco(alto: Float): Canvas {
        val abierto = lienzo
        if (abierto != null && altura + alto <= ALTO_A4 - MARGEN) return abierto
        if (abierto != null) cerrarPagina()
        return abrirPagina()
    }

    private fun abrirPagina(): Canvas {
        paginas++
        val hoja = pdf.startPage(PdfDocument.PageInfo.Builder(ANCHO_A4, ALTO_A4, paginas).create())
        pagina = hoja
        lienzo = hoja.canvas
        altura = MARGEN
        return hoja.canvas
    }

    private fun cerrarPagina() {
        pagina?.let(pdf::finishPage)
        pagina = null
        lienzo = null
        altura = MARGEN
    }

    /**
     * Parte el texto en líneas que caben en [ancho].
     *
     * Se mide con la fuente de verdad y se corta por el último espacio que quepa; una
     * palabra más larga que la línea entera —un enlace, una referencia— se parte por
     * donde toque, que es feo pero es lo que hace cualquier procesador de textos antes
     * de dejarla salirse del papel.
     */
    private fun repartir(
        texto: String,
        pincel: Paint,
        ancho: Float,
    ): List<String> {
        val lineas = mutableListOf<String>()
        texto.split('\n').forEach { trozo ->
            var resto = trozo.replace('\t', ' ').trim()
            if (resto.isEmpty()) lineas += ""
            while (resto.isNotEmpty()) {
                val caben = pincel.breakText(resto, true, ancho, null)
                if (caben >= resto.length) {
                    lineas += resto
                    break
                }
                val espacio = resto.lastIndexOf(' ', caben)
                val corte = if (espacio > 0) espacio else caben.coerceAtLeast(1)
                lineas += resto.substring(0, corte).trimEnd()
                resto = resto.substring(corte).trimStart()
            }
        }
        return lineas
    }

    private fun alturaDeLinea(pincel: Paint): Float = pincel.textSize * INTERLINEADO

    private companion object {
        /** A4 en puntos PDF, que es la unidad en la que trabaja `PdfDocument`. */
        const val ANCHO_A4 = 595
        const val ALTO_A4 = 842
        const val MARGEN = 56f
        const val ANCHO_UTIL = ANCHO_A4 - 2 * MARGEN

        const val CUERPO_PT = 11f
        const val INTERLINEADO = 1.35f
        const val ESPACIO_ENTRE_PARRAFOS = 6f
        const val ESPACIO_ANTES_DE_TITULO = 10f
        const val RELLENO_DE_CELDA = 4f
        const val GROSOR_LINEA = 0.7f

        val TAMANOS_DE_TITULO = floatArrayOf(20f, 17f, 14f, 12.5f, 11.5f, 11f)

        /**
         * Sans-serif del sistema, que en Android es Roboto y trae el alfabeto latino
         * entero. Pedir una fuente concreta que el móvil no tenga acabaría en la de
         * repuesto, y ésa sí puede no tener las eñes.
         */
        fun pincel(
            tamano: Float,
            negrita: Boolean,
        ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, if (negrita) Typeface.BOLD else Typeface.NORMAL)
            textSize = tamano
        }
    }
}
