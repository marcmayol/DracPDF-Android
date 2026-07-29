package com.marcmayol.dracpdf.dominio.puertos

import com.marcmayol.dracpdf.dominio.modelo.Firma
import com.marcmayol.dracpdf.dominio.modelo.IdFirma

/**
 * La biblioteca de firmas dibujadas.
 *
 * Vive en el almacenamiento privado de la aplicación y no en el del sistema: una
 * firma manuscrita es de quien la dibujó y no tiene por qué aparecer en la galería
 * del teléfono ni en un selector de ficheros compartido.
 */
interface AlmacenFirmas {
    /**
     * Guarda una firma nueva y devuelve su ficha.
     *
     * El alta tiene que ser **atómica**: hasta que no está entera, no está. Si el
     * proceso muere a mitad —cosa que en un móvil pasa—, lo que no puede quedar es
     * una firma a medias que luego se dibuje cortada.
     */
    fun guardar(
        png: ByteArray,
        anchoPx: Int,
        altoPx: Int,
        nombre: String,
    ): Firma

    /** Las firmas guardadas, de la más reciente a la más antigua. */
    fun listar(): List<Firma>

    /** Los píxeles de una firma, para dibujarla o estamparla. */
    fun leer(id: IdFirma): ByteArray

    fun borrar(id: IdFirma)
}
