package com.marcmayol.dracpdf.adaptadores.mupdf

import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.PDFAnnotation
import com.artifex.mupdf.fitz.PDFPage
import com.artifex.mupdf.fitz.Rect
import com.marcmayol.dracpdf.dominio.modelo.Estampado
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.puertos.StampService

/**
 * El estampado sobre MuPDF: la firma va como **anotación de sello** con la imagen
 * dentro.
 *
 * Es lo que hace Acrobat y lo que entiende cualquier visor. La alternativa —fundir
 * los píxeles en el contenido de la página— dejaría la firma imposible de quitar y
 * habría que reescribir el flujo de contenido de la página entera para ponerla, con
 * lo que eso arrastra en un PDF que puede venir comprimido y con recursos
 * compartidos entre páginas.
 *
 * El PNG llega con canal alfa y hay que conservarlo: MuPDF construye la imagen del
 * PDF con su máscara de transparencia, así que lo que se estampa es el trazo y no
 * un recuadro blanco alrededor del trazo.
 */
class MuPdfStampService(
    private val sesiones: SesionesMuPdf,
) : StampService {
    override fun estampar(
        id: IdDocumento,
        pagina: Int,
        marco: RectPt,
        png: ByteArray,
    ): Estampado =
        sesiones.en(id) { documento ->
            val hoja =
                documento.loadPage(pagina) as? PDFPage
                    ?: throw IllegalArgumentException("La página $pagina no admite anotaciones")
            try {
                conObjetos { objetos ->
                    val imagen = Image(png)
                    try {
                        val sello =
                            checkNotNull(objetos.anotarAnotacion(hoja.createAnnotation(PDFAnnotation.TYPE_STAMP))) {
                                "MuPDF no ha creado la anotación de sello"
                            }

                        // El orden importa: primero la imagen, que fija la apariencia
                        // con la proporción del PNG, y después el marco, que es quien
                        // manda dónde y de qué tamaño se dibuja.
                        sello.setStampImage(imagen)
                        sello.setRect(Rect(marco.x0, marco.y0, marco.x1, marco.y1))
                        sello.update()
                    } finally {
                        imagen.destroy()
                    }
                }
                Estampado(pagina = pagina, marco = marco)
            } finally {
                hoja.destroy()
            }
        }
}
