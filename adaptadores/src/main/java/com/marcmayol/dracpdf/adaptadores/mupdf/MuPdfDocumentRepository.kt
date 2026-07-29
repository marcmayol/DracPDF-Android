package com.marcmayol.dracpdf.adaptadores.mupdf

import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.SeekableInputStream
import com.artifex.mupdf.fitz.SeekableStream
import com.marcmayol.dracpdf.adaptadores.saf.FlujoEscritura
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentos
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
    private val fuente: FuenteDocumentos,
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

    override fun tieneCambiosSinGuardar(id: IdDocumento): Boolean =
        sesiones.en(id) { documento ->
            (documento as? PDFDocument)?.hasUnsavedChanges() ?: false
        }

    /**
     * Escribe una revisión nueva al final del fichero, con lo que ha cambiado.
     *
     * Si el documento no admite guardado incremental —los hay: un PDF reparado al
     * abrirlo, o uno cuyo índice de objetos MuPDF ha tenido que reconstruir— se
     * escribe entero. Es lo único que se puede hacer, y no es gratis: el fichero
     * pierde sus revisiones anteriores. Por eso se comprueba y no se asume.
     */
    override fun guardarIncremental(id: IdDocumento) {
        val origen = sesiones.origenDe(id)
        sesiones.en(id) { documento ->
            val pdf = documento as? PDFDocument ?: throw ErrorDocumento.NoSePuedeLeer(origen)
            val opciones = if (pdf.canBeSavedIncrementally()) OPCIONES_INCREMENTAL else ""
            fuente.abrirParaEscribir(origen).use { salida ->
                pdf.save(salida, opciones)
            }
        }
    }

    override fun origenDe(id: IdDocumento): OrigenDocumento = sesiones.origenDe(id)

    /**
     * Copia el fichero tal cual, sin pasar por el motor.
     *
     * Se copian los bytes y no el documento en memoria a propósito: una copia hecha
     * volcando el documento sería un PDF reescrito por MuPDF, con sus revisiones
     * aplanadas y las firmas previas arruinadas. La copia editable de un documento
     * firmado tiene que arrancar siendo idéntica al original.
     */
    override fun copiarA(
        id: IdDocumento,
        destino: OrigenDocumento,
    ) {
        val origen = sesiones.origenDe(id)
        fuente.abrirParaEscribir(destino).use { salida ->
            val entrada = fuente.abrir(origen)
            try {
                copiar(entrada, salida)
            } finally {
                (entrada as? AutoCloseable)?.let { runCatching { it.close() } }
            }
        }
    }

    private fun copiar(
        entrada: SeekableInputStream,
        salida: FlujoEscritura,
    ) {
        entrada.seek(0, SeekableStream.SEEK_SET)
        salida.seek(0, SeekableStream.SEEK_SET)
        val bufer = ByteArray(TAMANO_BUFER)
        while (true) {
            val leidos = entrada.read(bufer)
            if (leidos <= 0) break
            salida.write(bufer, 0, leidos)
        }
        // Si el destino ya existía y era más largo, lo que sobra tiene que irse: si no,
        // la cola del fichero viejo quedaría pegada detrás de la copia.
        salida.truncate()
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

    private companion object {
        /**
         * Lo que MuPDF entiende por «añade una revisión y no toques lo de antes».
         * `compress` va con él porque la revisión nueva no tiene por qué ser grande.
         */
        const val OPCIONES_INCREMENTAL = "incremental,compress"

        const val TAMANO_BUFER = 64 * 1024
    }
}
