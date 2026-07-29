package com.marcmayol.dracpdf.adaptadores.mupdf

import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.SeekableInputStream
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentos
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Los documentos que MuPDF tiene abiertos, cada uno con su hilo.
 *
 * **Un hilo por documento, sin excepciones.** MuPDF no es seguro entre hilos: un
 * `Document` sólo puede tocarse desde el hilo que lo abrió. Saltarse eso no da una
 * excepción de Java que se pueda capturar y registrar; da un `SIGSEGV` que se lleva
 * el proceso por delante y deja un volcado nativo ilegible. Por eso cada documento
 * abierto viene con su hilo propio, todas sus operaciones pasan por él, y el hilo
 * muere cuando el documento se cierra.
 *
 * Esta clase existe separada de los adaptadores porque hay más de uno hablando con
 * el mismo documento —el repositorio lo rasteriza y el servicio de formularios lo
 * rellena—, y los dos tienen que entrar por el mismo hilo. Si cada uno guardara su
 * propio `Document`, serían dos documentos distintos: los cambios de uno no los
 * vería el otro, y el segundo hilo tocando el mismo fichero es justo el SIGSEGV que
 * todo esto evita.
 */
class SesionesMuPdf(
    private val fuente: FuenteDocumentos,
) {
    private val documentos = ConcurrentHashMap<IdDocumento, Sesion>()

    /**
     * Abre el documento bajo [id] y ejecuta [inicial] dentro de su hilo, con el
     * documento ya autenticado. La sesión sólo queda registrada si [inicial] termina
     * bien: si lanza —porque el documento no tiene páginas legibles, por ejemplo— se
     * deshace todo y no queda ni hilo ni descriptor de fichero vivos.
     *
     * El `catch (Throwable)` es a propósito y no puede ser más concreto: pase lo que
     * pase al abrir —error de MuPDF, contraseña, o un fallo del propio hilo— hay que
     * cerrar el flujo y matar el hilo antes de dejar salir la excepción, o cada
     * intento fallido deja un descriptor de fichero y un hilo vivos para siempre.
     */
    @Suppress("TooGenericExceptionCaught")
    fun <T> abrir(
        id: IdDocumento,
        origen: OrigenDocumento,
        contrasena: String?,
        inicial: (Document) -> T,
    ): T {
        val hilo = Executors.newSingleThreadExecutor { tarea -> Thread(tarea, "mupdf-${id.valor}") }
        val flujo = fuente.abrir(origen)

        val documento =
            try {
                enHilo(hilo) { abrirEnHilo(flujo, origen, contrasena) }
            } catch (e: Throwable) {
                cerrarCrudo(hilo, flujo, documento = null)
                throw e
            }

        val resultado =
            try {
                enHilo(hilo) { inicial(documento) }
            } catch (e: Throwable) {
                cerrarCrudo(hilo, flujo, documento)
                throw e
            }

        documentos[id] = Sesion(documento, flujo, hilo, origen)
        return resultado
    }

    /** De dónde salió el documento, que es también dónde hay que guardarlo. */
    fun origenDe(id: IdDocumento): OrigenDocumento = documentos[id]?.origen ?: throw ErrorDocumento.NoEstaAbierto(id)

    /**
     * Ejecuta [bloque] en el hilo del documento [id] y espera el resultado.
     *
     * La espera es deliberada: los puertos del dominio son síncronos y quien llama ya
     * está en un hilo de trabajo, así que aquí no se bloquea ninguna interfaz.
     */
    fun <T> en(
        id: IdDocumento,
        bloque: (Document) -> T,
    ): T {
        val sesion = documentos[id] ?: throw ErrorDocumento.NoEstaAbierto(id)
        return enHilo(sesion.hilo) { bloque(sesion.documento) }
    }

    fun estaAbierto(id: IdDocumento): Boolean = documentos.containsKey(id)

    fun cerrar(id: IdDocumento) {
        val sesion = documentos.remove(id) ?: return
        cerrarCrudo(sesion.hilo, sesion.flujo, sesion.documento)
    }

    fun cerrarTodo() {
        documentos.keys.toList().forEach(::cerrar)
    }

    private fun cerrarCrudo(
        hilo: ExecutorService,
        flujo: SeekableInputStream,
        documento: Document?,
    ) {
        if (documento != null) {
            runCatching { enHilo(hilo) { documento.destroy() } }
        }
        hilo.shutdownNow()
        (flujo as? AutoCloseable)?.let { runCatching { it.close() } }
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

    private class Sesion(
        val documento: Document,
        val flujo: SeekableInputStream,
        val hilo: ExecutorService,
        val origen: OrigenDocumento,
    )

    private companion object {
        /** Lo que MuPDF usa para elegir el intérprete; aquí siempre es PDF. */
        const val MAGIC_PDF = "application/pdf"
    }
}
