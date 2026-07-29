package com.marcmayol.dracpdf.dominio.puertos

import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento

/**
 * Sitio donde escribir a medias, y cómo poner lo escrito en su lugar cuando ya está
 * completo.
 *
 * Existe porque firmar no se puede hacer sobre el fichero final: se construye la
 * revisión firmada aparte y se pone encima de una vez. Si el proceso muere a mitad
 * —en un móvil, siempre puede—, lo que se pierde es el temporal y no el documento del
 * usuario.
 */
interface EspacioTemporal {
    /** Un sitio vacío donde escribir, con un nombre parecido al del original. */
    fun nuevo(nombreParecidoA: String): OrigenDocumento

    /**
     * Pone [temporal] en el lugar de [destino] de una sola vez.
     *
     * «De una sola vez» es lo que hay que garantizar: nadie puede llegar a ver el
     * documento medio escrito. Cómo se consigue depende de dónde esté el destino, y
     * eso ya no es asunto del dominio.
     */
    fun reemplazar(
        destino: OrigenDocumento,
        temporal: OrigenDocumento,
    )

    fun borrar(temporal: OrigenDocumento)
}
