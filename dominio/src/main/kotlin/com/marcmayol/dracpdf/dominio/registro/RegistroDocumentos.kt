package com.marcmayol.dracpdf.dominio.registro

import com.marcmayol.dracpdf.dominio.modelo.DocumentoAbierto
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.Marca
import java.util.concurrent.atomic.AtomicLong

/**
 * Lo que la aplicación sabe de un documento abierto además de su contenido: por
 * dónde iba y a qué zoom. En el móvil esto importa más que en el escritorio, porque
 * el proceso puede morir en cualquier momento y hay que poder volver al mismo sitio.
 */
data class EstadoDocumento(
    val documento: DocumentoAbierto,
    val paginaActual: Int = 0,
    val zoom: Float = 1f,
) {
    val id: IdDocumento get() = documento.id
}

/**
 * Registro de documentos abiertos: dueño único del ciclo de vida y de las marcas,
 * como en el escritorio. Nadie más guarda esa información, para que no haya dos
 * versiones de la verdad.
 *
 * Es seguro entre hilos: la interfaz lo lee desde el hilo principal y los casos de
 * uso lo escriben desde hilos de trabajo.
 */
class RegistroDocumentos {
    private val documentos = LinkedHashMap<IdDocumento, EstadoDocumento>()
    private val contador = AtomicLong(0)
    private val cerrojo = Any()

    /** Un identificador de sesión nuevo. No se deriva del fichero a propósito. */
    fun nuevoId(): IdDocumento = IdDocumento("doc-${contador.incrementAndGet()}")

    fun registrar(documento: DocumentoAbierto): EstadoDocumento =
        synchronized(cerrojo) {
            val estado = EstadoDocumento(documento)
            documentos[documento.id] = estado
            estado
        }

    fun estado(id: IdDocumento): EstadoDocumento =
        synchronized(cerrojo) {
            documentos[id] ?: throw ErrorDocumento.NoEstaAbierto(id)
        }

    fun estaAbierto(id: IdDocumento): Boolean = synchronized(cerrojo) { id in documentos }

    /** Los documentos abiertos, en el orden en que se abrieron. */
    fun abiertos(): List<EstadoDocumento> = synchronized(cerrojo) { documentos.values.toList() }

    fun anotarPagina(
        id: IdDocumento,
        pagina: Int,
    ) = actualizar(id) { it.copy(paginaActual = pagina) }

    fun anotarZoom(
        id: IdDocumento,
        zoom: Float,
    ) = actualizar(id) { it.copy(zoom = zoom) }

    fun marcar(
        id: IdDocumento,
        marca: Marca,
    ) = actualizar(id) { it.copy(documento = it.documento.copy(marcas = it.documento.marcas + marca)) }

    fun desmarcar(
        id: IdDocumento,
        marca: Marca,
    ) = actualizar(id) { it.copy(documento = it.documento.copy(marcas = it.documento.marcas - marca)) }

    fun quitar(id: IdDocumento) {
        synchronized(cerrojo) { documentos.remove(id) }
    }

    /**
     * Si hay algo sin guardar en cualquier documento. Lo pregunta el sistema de
     * actualización y lo preguntará el cierre de la aplicación: nadie debería
     * perder trabajo porque el proceso se fue.
     */
    fun hayCambiosSinGuardar(): Boolean =
        synchronized(cerrojo) {
            documentos.values.any { it.documento.tieneCambiosSinGuardar }
        }

    private fun actualizar(
        id: IdDocumento,
        cambio: (EstadoDocumento) -> EstadoDocumento,
    ) {
        synchronized(cerrojo) {
            val actual = documentos[id] ?: throw ErrorDocumento.NoEstaAbierto(id)
            documentos[id] = cambio(actual)
        }
    }
}
