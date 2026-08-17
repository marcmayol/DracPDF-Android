package com.marcmayol.dracpdf.adaptadores.mupdf

import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.PDFAnnotation
import com.artifex.mupdf.fitz.PDFPage
import com.artifex.mupdf.fitz.Rect
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.puertos.EdicionPdf
import com.marcmayol.dracpdf.dominio.puertos.ImagenEnPagina

/**
 * Editar el contenido de la página con MuPDF.
 *
 * Las dos operaciones que quitan cosas —borrar una imagen y corregir un texto— se
 * hacen con **redacciones**, que es la herramienta que el formato tiene para eso: se
 * marca un rectángulo, se le dice al motor qué hacer con lo que caiga dentro y él
 * reescribe el contenido. Hacerlo a mano significaría manipular el flujo de dibujo,
 * que puede venir comprimido, partido en varios trozos y compartido con otras
 * páginas.
 *
 * Y sobre todo: una redacción **borra**. Un rectángulo blanco encima del texto viejo
 * lo deja intacto debajo, y cualquiera lo recupera seleccionando y copiando. Eso no
 * es corregir un documento, es esconder algo en un documento.
 */
class MuPdfEdicion(
    private val sesiones: SesionesMuPdf,
) : EdicionPdf {
    override fun imagenesDe(
        id: IdDocumento,
        pagina: Int,
    ): List<ImagenEnPagina> =
        enLaPagina(id, pagina) { hoja ->
            val hojaEntera = hoja.bounds
            val observador = DispositivoDeImagenes()
            try {
                // Se «dibuja» la página contra un dispositivo que no dibuja: es la
                // forma de que el motor cuente qué imágenes hay y dónde.
                hoja.run(observador, Matrix.Identity(), null)
            } finally {
                observador.close()
            }

            observador.imagenes.map { contorno ->
                ImagenEnPagina(
                    pagina = pagina,
                    marco = contorno,
                    esLaPaginaEntera = ocupaLaHoja(contorno, hojaEntera),
                )
            }
        }

    override fun anadirImagen(
        id: IdDocumento,
        pagina: Int,
        marco: RectPt,
        imagen: ByteArray,
    ) {
        enLaPagina(id, pagina) { hoja ->
            conObjetos { objetos ->
                val dibujo = Image(imagen)
                try {
                    // Va como anotación de sello, igual que la firma: es lo que
                    // entiende cualquier visor y lo que se puede quitar después sin
                    // reescribir la página entera.
                    val sello =
                        checkNotNull(objetos.anotarAnotacion(hoja.createAnnotation(PDFAnnotation.TYPE_STAMP))) {
                            "MuPDF no ha creado la anotación de la imagen"
                        }
                    sello.setStampImage(dibujo)
                    sello.setRect(Rect(marco.x0, marco.y0, marco.x1, marco.y1))
                    sello.update()
                } finally {
                    dibujo.destroy()
                }
            }
        }
    }

    override fun quitarImagen(
        id: IdDocumento,
        pagina: Int,
        marco: RectPt,
    ) {
        redactar(id, pagina, marco, quitandoTexto = false)
    }

    override fun corregirTexto(
        id: IdDocumento,
        pagina: Int,
        marco: RectPt,
        nuevo: String,
        tamano: Float,
    ): Boolean {
        if (!cabe(nuevo, marco, tamano)) return false

        redactar(id, pagina, marco, quitandoTexto = true)
        // Y se vuelve a escribir en el mismo hueco. El texto nuevo entra como
        // anotación de texto libre, que lleva su apariencia dentro y se ve igual en
        // cualquier visor.
        enLaPagina(id, pagina) { hoja ->
            conObjetos { objetos ->
                val escrito =
                    checkNotNull(objetos.anotarAnotacion(hoja.createAnnotation(PDFAnnotation.TYPE_FREE_TEXT))) {
                        "MuPDF no ha creado el texto corregido"
                    }
                escrito.setRect(Rect(marco.x0, marco.y0, marco.x1, marco.y1))
                escrito.setDefaultAppearance(FUENTE, tamano, floatArrayOf(0f, 0f, 0f))
                escrito.contents = nuevo
                escrito.update()
            }
        }
        return true
    }

    /**
     * Marca el rectángulo y aplica la redacción.
     *
     * `blackBoxes` va a `false` a propósito: el recuadro negro que el motor pinta por
     * defecto es lo correcto en un documento censurado, y lo contrario de lo que se
     * quiere al corregir una línea o al quitar una foto de un folleto.
     */
    private fun redactar(
        id: IdDocumento,
        pagina: Int,
        marco: RectPt,
        quitandoTexto: Boolean,
    ) {
        enLaPagina(id, pagina) { hoja ->
            conObjetos { objetos ->
                val redaccion =
                    checkNotNull(objetos.anotarAnotacion(hoja.createAnnotation(PDFAnnotation.TYPE_REDACT))) {
                        "MuPDF no ha creado la redacción"
                    }
                redaccion.setRect(Rect(marco.x0, marco.y0, marco.x1, marco.y1))
                redaccion.update()
            }

            hoja.applyRedactions(
                false,
                PDFPage.REDACT_IMAGE_REMOVE,
                PDFPage.REDACT_LINE_ART_NONE,
                if (quitandoTexto) PDFPage.REDACT_TEXT_REMOVE else PDFPage.REDACT_TEXT_NONE,
            )
            hoja.update()
        }
    }

    /**
     * Si el texto nuevo cabe donde estaba el viejo.
     *
     * La cuenta es aproximada —el ancho medio de un carácter es algo más de medio
     * cuerpo en las tipografías normales— y se queda corta a propósito: es preferible
     * decir «no cabe» y dejar decidir, que meter un texto que sale de la caja y
     * aparece cortado al abrir el documento en otro visor.
     */
    private fun cabe(
        texto: String,
        marco: RectPt,
        tamano: Float,
    ): Boolean {
        val porLinea = (marco.ancho / (tamano * ANCHO_MEDIO_DE_LETRA)).toInt()
        val lineas = (marco.alto / (tamano * INTERLINEADO)).toInt()
        return porLinea > 0 && lineas > 0 && texto.length <= porLinea * lineas
    }

    private fun ocupaLaHoja(
        contorno: RectPt,
        hoja: Rect,
    ): Boolean {
        val anchoHoja = hoja.x1 - hoja.x0
        val altoHoja = hoja.y1 - hoja.y0
        if (anchoHoja <= 0f || altoHoja <= 0f) return false
        return contorno.ancho / anchoHoja >= FRACCION_PAGINA_ENTERA &&
            contorno.alto / altoHoja >= FRACCION_PAGINA_ENTERA
    }

    private fun <T> enLaPagina(
        id: IdDocumento,
        pagina: Int,
        bloque: (PDFPage) -> T,
    ): T =
        sesiones.en(id) { documento ->
            val hoja =
                documento.loadPage(pagina) as? PDFPage
                    ?: throw IllegalArgumentException("La página $pagina no se puede editar")
            try {
                bloque(hoja)
            } finally {
                hoja.destroy()
            }
        }

    private companion object {
        const val FUENTE = "Helv"

        /** A partir de aquí, una imagen es «la hoja»: casi siempre, un escaneo. */
        const val FRACCION_PAGINA_ENTERA = 0.9f

        const val ANCHO_MEDIO_DE_LETRA = 0.55f
        const val INTERLINEADO = 1.2f
    }
}
