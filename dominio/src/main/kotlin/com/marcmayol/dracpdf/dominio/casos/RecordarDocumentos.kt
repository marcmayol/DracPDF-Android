package com.marcmayol.dracpdf.dominio.casos

import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.AlmacenRecientes
import com.marcmayol.dracpdf.dominio.puertos.DocumentRepository
import com.marcmayol.dracpdf.dominio.puertos.DocumentoReciente
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos

/**
 * Los recientes, y por dónde iba cada uno.
 *
 * Las dos cosas van juntas porque son la misma promesa: **volver a donde lo dejaste**.
 * Una lista de recientes que reabre siempre por la página uno cumple la mitad, y en un
 * manual de doscientas páginas la mitad que no cumple es justo la que importaba.
 *
 * La posición se guarda al cerrar y no en cada scroll: escribir un fichero por página
 * que pasa sería castigar el disco por nada, y lo que se pierde si el proceso muere de
 * golpe es un número de página.
 */
class RecordarDocumentos(
    private val recientes: AlmacenRecientes,
    private val registro: RegistroDocumentos,
    private val repositorio: DocumentRepository,
) {
    /**
     * Apunta el documento recién abierto y devuelve por dónde iba, si se sabe.
     *
     * La posición se aplica al registro aquí mismo: quien abre no tiene que acordarse
     * de restaurarla, y así no hay dos maneras de abrir un documento.
     */
    fun alAbrir(
        id: IdDocumento,
        origen: OrigenDocumento,
        nombre: String,
        permisoPersistido: Boolean,
    ) {
        val anterior = recientes.listar().firstOrNull { it.origen.identificador == origen.identificador }

        recientes.anotar(
            DocumentoReciente(
                origen = origen,
                nombre = nombre,
                visto = System.currentTimeMillis(),
                pagina = anterior?.pagina ?: 0,
                zoom = anterior?.zoom ?: 1f,
                permisoPersistido = permisoPersistido,
            ),
        )

        anterior?.let { guardado ->
            // Sólo si la página sigue existiendo: el documento puede haber cambiado de
            // tamaño desde la última vez —organizado, dividido— y abrir en una página
            // que ya no está sería peor que abrir por el principio.
            val paginas = registro.estado(id).documento.paginas
            if (guardado.pagina in 0 until paginas) {
                registro.anotarPagina(id, guardado.pagina)
                registro.anotarZoom(id, guardado.zoom)
            }
        }
    }

    /**
     * Guarda por dónde se quedó el documento que se cierra.
     *
     * Se le pregunta al motor de dónde salió en vez de pedírselo a quien cierra: es el
     * mismo origen con el que se abrió, y hacer que cada sitio que cierra un documento
     * tenga que acordarse de traerlo era pedir que tarde o temprano se olvidara.
     */
    fun alCerrar(id: IdDocumento) {
        val estado = runCatching { registro.estado(id) }.getOrNull() ?: return
        val origen = runCatching { repositorio.origenDe(id) }.getOrNull() ?: return
        recientes.anotarPosicion(origen, estado.paginaActual, estado.zoom)
    }

    fun listar(): List<DocumentoReciente> = recientes.listar()

    fun olvidar(origen: OrigenDocumento) = recientes.olvidar(origen)

    fun olvidarTodos() = recientes.olvidarTodos()
}
