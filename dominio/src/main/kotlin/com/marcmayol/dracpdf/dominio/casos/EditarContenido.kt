package com.marcmayol.dracpdf.dominio.casos

import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.Marca
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.puertos.EdicionPdf
import com.marcmayol.dracpdf.dominio.puertos.ImagenEnPagina
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos

/**
 * Lo que se le puede hacer al contenido de la página: poner una imagen, quitarla y
 * corregir un texto.
 *
 * Esto pesa más que anotar y por eso avisa más. Una anotación se quita desde
 * cualquier visor; lo que se toca aquí **cambia el documento**, y quitar la imagen de
 * un escaneo deja una hoja en blanco. La regla de la casa se mantiene: un documento
 * firmado no se toca, y nada de esto guarda por su cuenta.
 */
class EditarContenido(
    private val edicion: EdicionPdf,
    private val registro: RegistroDocumentos,
) {
    fun imagenesDe(
        id: IdDocumento,
        pagina: Int,
    ): List<ImagenEnPagina> = edicion.imagenesDe(id, pagina)

    fun anadirImagen(
        id: IdDocumento,
        pagina: Int,
        marco: RectPt,
        imagen: ByteArray,
    ) {
        exigirQueSePuedaTocar(id, pagina)
        require(imagen.isNotEmpty()) { "No hay imagen que poner" }
        require(marco.ancho > 0f && marco.alto > 0f) { "Una imagen de tamaño cero no se vería" }

        edicion.anadirImagen(id, pagina, marco, imagen)
        registro.marcar(id, Marca.CAMBIOS_SIN_GUARDAR)
    }

    /**
     * Quita una imagen del contenido.
     *
     * Se le pasa la imagen entera y no un rectángulo suelto **a propósito**: quien la
     * quita ha tenido que listarla antes, y así el aviso de «esto es la hoja entera»
     * viaja hasta aquí en vez de perderse por el camino.
     */
    fun quitarImagen(
        id: IdDocumento,
        imagen: ImagenEnPagina,
    ) {
        exigirQueSePuedaTocar(id, imagen.pagina)
        edicion.quitarImagen(id, imagen.pagina, imagen.marco)
        registro.marcar(id, Marca.CAMBIOS_SIN_GUARDAR)
    }

    /**
     * Cambia un texto por otro.
     *
     * @return `false` si el texto nuevo no cabe donde estaba el viejo. No se recorta
     *   ni se encoge la letra sin avisar: el documento es de quien lo escribió, y una
     *   corrección que cambia el aspecto sin decirlo es peor que una que no se hace.
     */
    fun corregirTexto(
        id: IdDocumento,
        pagina: Int,
        marco: RectPt,
        nuevo: String,
        tamano: Float = TAMANO_POR_DEFECTO,
    ): Boolean {
        exigirQueSePuedaTocar(id, pagina)
        require(nuevo.isNotBlank()) { "Para dejarlo en blanco hay que borrar, no corregir" }

        val hecho = edicion.corregirTexto(id, pagina, marco, nuevo, tamano)
        if (hecho) registro.marcar(id, Marca.CAMBIOS_SIN_GUARDAR)
        return hecho
    }

    private fun exigirQueSePuedaTocar(
        id: IdDocumento,
        pagina: Int,
    ) {
        val estado = registro.estado(id)
        if (estado.documento.estaFirmado) throw ErrorDocumento.DocumentoFirmado(id)
        require(pagina in 0 until estado.documento.paginas) {
            "La página $pagina no existe: «${estado.documento.nombre}» tiene ${estado.documento.paginas}"
        }
    }

    private companion object {
        const val TAMANO_POR_DEFECTO = 12f
    }
}
