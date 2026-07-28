package com.marcmayol.dracpdf.adaptadores.mupdf

import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.SeekableInputStream
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentos
import com.marcmayol.dracpdf.dominio.modelo.DocumentoAbierto
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.modelo.PaginaRenderizada
import com.marcmayol.dracpdf.dominio.modelo.TamanoPt
import com.marcmayol.dracpdf.dominio.puertos.DocumentRepository
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * El puerto del documento, implementado con MuPDF: el mismo motor C que PyMuPDF usa
 * en el DracPDF de escritorio, y en la misma versión.
 *
 * **Un hilo por documento, sin excepciones.** MuPDF no es seguro entre hilos: un
 * `Document` sólo puede tocarse desde el hilo que lo abrió. Saltarse eso no da una
 * excepción de Java que se pueda capturar y registrar; da un `SIGSEGV` que se lleva
 * el proceso por delante y deja un volcado nativo ilegible. Por eso cada documento
 * abierto viene con su hilo propio, todas sus operaciones pasan por él, y el hilo
 * muere cuando el documento se cierra.
 */
class MuPdfDocumentRepository(
    private val fuente: FuenteDocumentos,
) : DocumentRepository {
    private val documentos = ConcurrentHashMap<IdDocumento, DocumentoMuPdf>()

    /**
     * Cuántas páginas se han rasterizado desde que arrancó. Es la métrica con la que
     * se demuestra que el visor sólo dibuja lo que se ve: si al abrir un documento de
     * 500 páginas esto marca 500, el render perezoso no está funcionando.
     */
    private val contadorRenders = AtomicInteger(0)

    val paginasRenderizadas: Int get() = contadorRenders.get()

    // El `catch (Throwable)` de abajo es a propósito y no puede ser más concreto: pase
    // lo que pase al abrir —error de MuPDF, contraseña, o un fallo del propio hilo— hay
    // que cerrar el flujo y matar el hilo antes de dejar salir la excepción, o cada
    // intento fallido deja un descriptor de fichero y un hilo vivos para siempre.
    @Suppress("TooGenericExceptionCaught")
    override fun abrir(
        id: IdDocumento,
        origen: OrigenDocumento,
        contrasena: String?,
    ): DocumentoAbierto {
        val hilo = Executors.newSingleThreadExecutor { tarea -> Thread(tarea, "mupdf-${id.valor}") }
        val flujo = fuente.abrir(origen)

        val documento =
            try {
                enHilo(hilo) { abrirEnHilo(flujo, origen, contrasena) }
            } catch (e: Throwable) {
                hilo.shutdownNow()
                (flujo as? AutoCloseable)?.let { runCatching { it.close() } }
                throw e
            }

        val paginas = enHilo(hilo) { documento.countPages() }
        if (paginas <= 0) {
            enHilo(hilo) { documento.destroy() }
            hilo.shutdownNow()
            throw ErrorDocumento.NoSePuedeLeer(origen)
        }

        documentos[id] = DocumentoMuPdf(documento, flujo, hilo)

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
    ): TamanoPt {
        val abierto = abierto(id)
        return enHilo(abierto.hilo) {
            val hoja = abierto.documento.loadPage(pagina)
            try {
                val caja = hoja.bounds
                TamanoPt(caja.x1 - caja.x0, caja.y1 - caja.y0)
            } finally {
                hoja.destroy()
            }
        }
    }

    override fun renderizar(
        id: IdDocumento,
        pagina: Int,
        escala: Float,
    ): PaginaRenderizada {
        val abierto = abierto(id)
        return enHilo(abierto.hilo) {
            val hoja = abierto.documento.loadPage(pagina)
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
    }

    override fun cerrar(id: IdDocumento) {
        val abierto = documentos.remove(id) ?: return
        enHilo(abierto.hilo) { abierto.documento.destroy() }
        (abierto.flujo as? AutoCloseable)?.let { runCatching { it.close() } }
        abierto.hilo.shutdown()
    }

    /** Cierra todo. Lo llama la aplicación al terminar. */
    fun cerrarTodo() {
        documentos.keys.toList().forEach(::cerrar)
    }

    // Tres salidas de error y una captura genérica, las dos cosas justificadas: MuPDF
    // señala *todo* lo que va mal dentro del C con una RuntimeException pelada, y abrir
    // un documento puede fallar de tres maneras que el usuario tiene que distinguir
    // —no se puede leer, falta la contraseña, la contraseña no vale—. Fundirlas en una
    // sola sería perder justo la información que hace falta para reaccionar.
    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private fun abrirEnHilo(
        flujo: SeekableInputStream,
        origen: OrigenDocumento,
        contrasena: String?,
    ): Document {
        val documento =
            try {
                Document.openDocument(flujo, MAGIC_PDF)
            } catch (e: RuntimeException) {
                // MuPDF lanza RuntimeException para todo lo que va mal dentro del C.
                throw ErrorDocumento.NoSePuedeLeer(origen, e)
            } catch (e: IOException) {
                throw ErrorDocumento.NoSePuedeLeer(origen, e)
            }

        if (documento.needsPassword()) {
            if (contrasena == null) {
                documento.destroy()
                throw ErrorDocumento.NecesitaContrasena(origen)
            }
            if (!documento.authenticatePassword(contrasena)) {
                documento.destroy()
                throw ErrorDocumento.ContrasenaIncorrecta(origen)
            }
        }
        return documento
    }

    private fun abierto(id: IdDocumento): DocumentoMuPdf = documentos[id] ?: throw ErrorDocumento.NoEstaAbierto(id)

    /**
     * Ejecuta en el hilo del documento y espera. La espera es deliberada: el puerto
     * es síncrono y quien llama ya está en un hilo de trabajo, así que aquí no se
     * bloquea ninguna interfaz.
     */
    private fun <T> enHilo(
        hilo: ExecutorService,
        bloque: () -> T,
    ): T =
        try {
            hilo.submit(bloque).get()
        } catch (e: ExecutionException) {
            // Se desenvuelve para que quien llama vea el ErrorDocumento que se lanzó
            // dentro y no un ExecutionException que no dice nada.
            throw e.cause ?: e
        }

    private fun nombreDe(origen: OrigenDocumento): String =
        when (origen) {
            is OrigenDocumento.Externo -> origen.nombre
            is OrigenDocumento.Privado -> origen.nombre
        }

    private class DocumentoMuPdf(
        val documento: Document,
        val flujo: SeekableInputStream,
        val hilo: ExecutorService,
    )

    private companion object {
        /** Lo que MuPDF usa para elegir el intérprete; aquí siempre es PDF. */
        const val MAGIC_PDF = "application/pdf"
    }
}
