package com.marcmayol.dracpdf.adaptadores.mupdf

import com.artifex.mupdf.fitz.PDFAnnotation
import com.artifex.mupdf.fitz.PDFPage
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.Rect
import com.marcmayol.dracpdf.dominio.modelo.Anotacion
import com.marcmayol.dracpdf.dominio.modelo.ColorAnotacion
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.modelo.TipoAnotacion
import com.marcmayol.dracpdf.dominio.puertos.AnotacionesPdf

/**
 * Las anotaciones, sobre MuPDF y como anotaciones **de verdad** del PDF.
 *
 * Un resaltado es un `Highlight` con sus *quad points*, no un rectángulo amarillo
 * pintado encima: eso es lo que hace que se vea igual en Adobe, que se pueda borrar
 * desde otro visor y que el texto de debajo se siga pudiendo seleccionar y buscar.
 *
 * Todo pasa por el hilo del documento y **ningún objeto nativo sale de aquí**: las
 * anotaciones de Java sostienen memoria del motor y soltarlas fuera de su hilo se
 * lleva el proceso por delante. De eso se encarga [conObjetos].
 */
class MuPdfAnotaciones(
    private val sesiones: SesionesMuPdf,
) : AnotacionesPdf {
    override fun listar(
        id: IdDocumento,
        pagina: Int,
    ): List<Anotacion> =
        enLaPagina(id, pagina) { hoja ->
            conObjetos { objetos ->
                hoja.annotations.orEmpty().mapIndexedNotNull { posicion, anotacion ->
                    objetos.anotarAnotacion(anotacion)
                    aDominio(anotacion, pagina, posicion)
                }
            }
        }

    override fun marcar(
        id: IdDocumento,
        pagina: Int,
        tipo: TipoAnotacion,
        marcos: List<RectPt>,
        color: ColorAnotacion,
    ): Anotacion {
        require(marcos.isNotEmpty()) { "No se ha dicho qué hay que marcar" }
        return enLaPagina(id, pagina) { hoja ->
            conObjetos { objetos ->
                val marca =
                    checkNotNull(objetos.anotarAnotacion(hoja.createAnnotation(deTipo(tipo)))) {
                        "MuPDF no ha creado la anotación"
                    }
                // Los *quad points* son lo que distingue una marca de texto de un
                // rectángulo: uno por línea, y el visor los dibuja siguiendo el
                // renglón aunque la selección empiece a mitad de una línea y acabe a
                // mitad de otra.
                marca.quadPoints = marcos.map { Quad(Rect(it.x0, it.y0, it.x1, it.y1)) }.toTypedArray()
                marca.setColor(floatArrayOf(color.rojo, color.verde, color.azul))
                marca.update()
                Anotacion(pagina, hoja.annotations.size - 1, tipo, marcos, color = color)
            }
        }
    }

    override fun anotar(
        id: IdDocumento,
        pagina: Int,
        marco: RectPt,
        texto: String,
        color: ColorAnotacion,
    ): Anotacion =
        enLaPagina(id, pagina) { hoja ->
            conObjetos { objetos ->
                val nota =
                    checkNotNull(objetos.anotarAnotacion(hoja.createAnnotation(PDFAnnotation.TYPE_TEXT))) {
                        "MuPDF no ha creado la nota"
                    }
                nota.setRect(Rect(marco.x0, marco.y0, marco.x1, marco.y1))
                nota.contents = texto
                nota.setColor(floatArrayOf(color.rojo, color.verde, color.azul))
                nota.update()
                Anotacion(pagina, hoja.annotations.size - 1, TipoAnotacion.NOTA, listOf(marco), texto, color)
            }
        }

    override fun escribir(
        id: IdDocumento,
        pagina: Int,
        marco: RectPt,
        texto: String,
        tamano: Float,
    ): Anotacion =
        enLaPagina(id, pagina) { hoja ->
            conObjetos { objetos ->
                val escrito =
                    checkNotNull(objetos.anotarAnotacion(hoja.createAnnotation(PDFAnnotation.TYPE_FREE_TEXT))) {
                        "MuPDF no ha creado el texto"
                    }
                escrito.setRect(Rect(marco.x0, marco.y0, marco.x1, marco.y1))
                // La fuente se pide por nombre y el motor **la embebe** al generar la
                // apariencia: es lo que hace que el documento se lea igual en un
                // teléfono que no tenga esa tipografía instalada.
                escrito.setDefaultAppearance(FUENTE_EMBEBIDA, tamano, floatArrayOf(0f, 0f, 0f))
                escrito.contents = texto
                escrito.update()
                Anotacion(pagina, hoja.annotations.size - 1, TipoAnotacion.TEXTO, listOf(marco), texto)
            }
        }

    override fun borrar(
        id: IdDocumento,
        pagina: Int,
        posicion: Int,
    ) {
        enLaPagina(id, pagina) { hoja ->
            conObjetos { objetos ->
                val anotaciones = hoja.annotations.orEmpty()
                anotaciones.forEach { objetos.anotarAnotacion(it) }
                val victima = anotaciones.getOrNull(posicion) ?: return@conObjetos
                hoja.deleteAnnotation(victima)
                hoja.update()
            }
        }
    }

    /**
     * Traduce una anotación del motor a la del dominio.
     *
     * Devuelve `null` para las que esta aplicación no gestiona —widgets de formulario,
     * sellos de firma, enlaces—: aparecerían en la lista de marcas y al borrarlas se
     * llevarían por delante un campo o una firma.
     */
    private fun aDominio(
        anotacion: PDFAnnotation,
        pagina: Int,
        posicion: Int,
    ): Anotacion? {
        val tipo =
            when (anotacion.type) {
                PDFAnnotation.TYPE_HIGHLIGHT -> TipoAnotacion.RESALTADO
                PDFAnnotation.TYPE_UNDERLINE -> TipoAnotacion.SUBRAYADO
                PDFAnnotation.TYPE_STRIKE_OUT -> TipoAnotacion.TACHADO
                PDFAnnotation.TYPE_TEXT -> TipoAnotacion.NOTA
                PDFAnnotation.TYPE_FREE_TEXT -> TipoAnotacion.TEXTO
                else -> return null
            }

        // Los *quad points* sólo existen en las marcas de texto. Pedírselos a una nota
        // o a un texto libre no devuelve una lista vacía: **lanza**, y se llevaría por
        // delante el listado entero de la página.
        val marcos =
            if (tipo.marcaTexto()) {
                anotacion.quadPoints
                    .orEmpty()
                    .map { it.toRect() }
                    .map { RectPt(it.x0, it.y0, it.x1, it.y1) }
            } else {
                listOfNotNull(anotacion.rect?.let { RectPt(it.x0, it.y0, it.x1, it.y1) })
            }

        return Anotacion(
            pagina = pagina,
            posicion = posicion,
            tipo = tipo,
            marcos = marcos,
            contenido = anotacion.contents.orEmpty(),
            color = colorDe(anotacion.color),
        )
    }

    /** Si es de las que se dibujan siguiendo el renglón del texto. */
    private fun TipoAnotacion.marcaTexto(): Boolean =
        this == TipoAnotacion.RESALTADO || this == TipoAnotacion.SUBRAYADO || this == TipoAnotacion.TACHADO

    /** El color declarado, redondeado al más cercano de los que ofrece la aplicación. */
    private fun colorDe(componentes: FloatArray?): ColorAnotacion {
        if (componentes == null || componentes.size < COMPONENTES_RGB) return ColorAnotacion.AMARILLO
        return ColorAnotacion.entries.minBy { candidato ->
            val dr = candidato.rojo - componentes[0]
            val dv = candidato.verde - componentes[1]
            val da = candidato.azul - componentes[2]
            dr * dr + dv * dv + da * da
        }
    }

    private fun deTipo(tipo: TipoAnotacion): Int =
        when (tipo) {
            TipoAnotacion.RESALTADO -> PDFAnnotation.TYPE_HIGHLIGHT
            TipoAnotacion.SUBRAYADO -> PDFAnnotation.TYPE_UNDERLINE
            TipoAnotacion.TACHADO -> PDFAnnotation.TYPE_STRIKE_OUT
            TipoAnotacion.NOTA -> PDFAnnotation.TYPE_TEXT
            TipoAnotacion.TEXTO -> PDFAnnotation.TYPE_FREE_TEXT
        }

    private fun <T> enLaPagina(
        id: IdDocumento,
        pagina: Int,
        bloque: (PDFPage) -> T,
    ): T =
        sesiones.en(id) { documento ->
            val hoja =
                documento.loadPage(pagina) as? PDFPage
                    ?: throw IllegalArgumentException("La página $pagina no admite anotaciones")
            try {
                bloque(hoja)
            } finally {
                hoja.destroy()
            }
        }

    private companion object {
        /**
         * La fuente de las anotaciones de texto.
         *
         * Helvetica es una de las catorce que todo lector de PDF trae de serie, así que
         * el documento se ve igual en todas partes sin engordar con una tipografía
         * dentro. Para el texto que se **añade al contenido** —Fase 8— sí hay que
         * embeber una OFL, porque ahí no hay anotación que genere la apariencia.
         */
        const val FUENTE_EMBEBIDA = "Helv"
        const val COMPONENTES_RGB = 3
    }
}
