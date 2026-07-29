package com.marcmayol.dracpdf.adaptadores.mupdf

import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Matrix
import com.marcmayol.dracpdf.dominio.modelo.DocumentoAbierto
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.modelo.PaginaRenderizada
import com.marcmayol.dracpdf.dominio.modelo.TamanoPt
import com.marcmayol.dracpdf.dominio.puertos.DocumentRepository
import java.util.concurrent.atomic.AtomicInteger

/**
 * El puerto del documento, implementado con MuPDF: el mismo motor C que PyMuPDF usa
 * en el DracPDF de escritorio, y en la misma versión.
 *
 * El ciclo de vida y la disciplina de hilos no están aquí sino en [SesionesMuPdf],
 * que es quien tiene los documentos abiertos y a la que también entran los demás
 * adaptadores del motor.
 */
class MuPdfDocumentRepository(
    private val sesiones: SesionesMuPdf,
) : DocumentRepository {
    /**
     * Cuántas páginas se han rasterizado desde que arrancó. Es la métrica con la que
     * se demuestra que el visor sólo dibuja lo que se ve: si al abrir un documento de
     * 500 páginas esto marca 500, el render perezoso no está funcionando.
     */
    private val contadorRenders = AtomicInteger(0)

    val paginasRenderizadas: Int get() = contadorRenders.get()

    override fun abrir(
        id: IdDocumento,
        origen: OrigenDocumento,
        contrasena: String?,
    ): DocumentoAbierto {
        val paginas =
            sesiones.abrir(id, origen, contrasena) { documento ->
                documento.countPages().also {
                    if (it <= 0) throw ErrorDocumento.NoSePuedeLeer(origen)
                }
            }

        return DocumentoAbierto(
            id = id,
            nombre = nombreDe(origen),
            paginas = paginas,
            // La marca FIRMADO llega en la Fase 4, junto con la verificación que la
            // sostiene: marcarlo ahora sería afirmar algo que todavía no se comprueba.
            marcas = emptySet(),
        )
    }

    override fun tamanoPagina(
        id: IdDocumento,
        pagina: Int,
    ): TamanoPt =
        sesiones.en(id) { documento ->
            val hoja = documento.loadPage(pagina)
            try {
                val caja = hoja.bounds
                TamanoPt(caja.x1 - caja.x0, caja.y1 - caja.y0)
            } finally {
                hoja.destroy()
            }
        }

    override fun renderizar(
        id: IdDocumento,
        pagina: Int,
        escala: Float,
    ): PaginaRenderizada =
        sesiones.en(id) { documento ->
            val hoja = documento.loadPage(pagina)
            try {
                // Con alfa, para que un PDF sin fondo no salga con basura detrás; el
                // resultado son cuatro bytes RGBA por píxel, que es justo lo que
                // espera un Bitmap ARGB_8888 de Android.
                val mapa = hoja.toPixmap(Matrix.Scale(escala), ColorSpace.DeviceRGB, true)
                try {
                    contadorRenders.incrementAndGet()
                    PaginaRenderizada(
                        pagina = pagina,
                        escala = escala,
                        ancho = mapa.width,
                        alto = mapa.height,
                        pixeles = mapa.samples,
                    )
                } finally {
                    mapa.destroy()
                }
            } finally {
                hoja.destroy()
            }
        }

    override fun cerrar(id: IdDocumento) {
        sesiones.cerrar(id)
    }

    /** Cierra todo. Lo llama la aplicación al terminar. */
    fun cerrarTodo() {
        sesiones.cerrarTodo()
    }

    private fun nombreDe(origen: OrigenDocumento): String =
        when (origen) {
            is OrigenDocumento.Externo -> origen.nombre
            is OrigenDocumento.Privado -> origen.nombre
        }
}
