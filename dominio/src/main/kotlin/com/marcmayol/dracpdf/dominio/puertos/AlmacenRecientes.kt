package com.marcmayol.dracpdf.dominio.puertos

import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento

/**
 * Un documento por el que se pasó, y por dónde iba.
 *
 * Guarda el origen entero y no sólo su nombre porque un reciente sirve para **volver
 * a abrirlo**, y para eso hace falta el identificador que el sistema entregó. El
 * nombre es lo que se enseña; el identificador, lo que funciona.
 */
data class DocumentoReciente(
    val origen: OrigenDocumento,
    val nombre: String,
    /** Cuándo se miró por última vez, en milisegundos desde época. */
    val visto: Long,
    val pagina: Int = 0,
    val zoom: Float = 1f,
    /**
     * Si el permiso de lectura se concedió para siempre.
     *
     * Los documentos que llegan compartidos desde otra aplicación traen un permiso que
     * muere con la sesión: se apuntan igual —el usuario los ha visto y espera
     * encontrarlos— pero sabiendo que mañana puede que ya no abran.
     */
    val permisoPersistido: Boolean = false,
)

/**
 * Los documentos recientes.
 *
 * Es una lista corta y ordenada por lo último que se miró; el almacén se encarga de
 * que no crezca sin fin y de que un mismo documento no aparezca dos veces.
 */
interface AlmacenRecientes {
    /** Del más reciente al más antiguo. */
    fun listar(): List<DocumentoReciente>

    /**
     * Apunta que se ha estado en este documento. Si ya estaba, se actualiza y sube al
     * primer puesto en vez de duplicarse.
     */
    fun anotar(reciente: DocumentoReciente)

    /** Deja por dónde iba el documento, sin tocar el resto de su ficha. */
    fun anotarPosicion(
        origen: OrigenDocumento,
        pagina: Int,
        zoom: Float,
    )

    fun olvidar(origen: OrigenDocumento)

    fun olvidarTodos()
}
