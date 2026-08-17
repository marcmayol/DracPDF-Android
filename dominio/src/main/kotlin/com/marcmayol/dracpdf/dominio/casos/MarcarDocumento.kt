package com.marcmayol.dracpdf.dominio.casos

import com.marcmayol.dracpdf.dominio.modelo.Anotacion
import com.marcmayol.dracpdf.dominio.modelo.ColorAnotacion
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.Marca
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.modelo.TipoAnotacion
import com.marcmayol.dracpdf.dominio.puertos.AnotacionesPdf
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos

/**
 * Resaltar, subrayar, tachar, pegar notas y escribir sobre el documento.
 *
 * Las cinco son la misma operación desde el punto de vista del documento —añadir una
 * anotación— y por eso comparten caso de uso: las mismas comprobaciones, la misma
 * marca de cambios y el mismo no a los documentos firmados.
 *
 * **No guarda.** Igual que rellenar un campo o estampar una firma: marcar cinco
 * párrafos seguidos dejaría cinco revisiones en el fichero, y guardar es una decisión
 * del usuario.
 */
class MarcarDocumento(
    private val anotaciones: AnotacionesPdf,
    private val registro: RegistroDocumentos,
) {
    /** Marca lo que hay seleccionado. */
    fun marcar(
        id: IdDocumento,
        pagina: Int,
        tipo: TipoAnotacion,
        marcos: List<RectPt>,
        color: ColorAnotacion = ColorAnotacion.AMARILLO,
    ): Anotacion {
        exigirQueSePuedaEscribir(id, pagina)
        require(marcos.isNotEmpty()) { "No se ha seleccionado nada que marcar" }
        require(tipo != TipoAnotacion.NOTA && tipo != TipoAnotacion.TEXTO) {
            "Las notas y el texto no se marcan sobre una selección: tienen su propia acción"
        }

        return anotaciones.marcar(id, pagina, tipo, marcos, color).also { anotado(id) }
    }

    /** Pega una nota. */
    fun anotar(
        id: IdDocumento,
        pagina: Int,
        marco: RectPt,
        texto: String,
        color: ColorAnotacion = ColorAnotacion.AMARILLO,
    ): Anotacion {
        exigirQueSePuedaEscribir(id, pagina)
        require(texto.isNotBlank()) { "Una nota vacía no dice nada" }

        return anotaciones.anotar(id, pagina, marco, texto, color).also { anotado(id) }
    }

    /** Escribe texto sobre la página. */
    fun escribir(
        id: IdDocumento,
        pagina: Int,
        marco: RectPt,
        texto: String,
        tamano: Float = TAMANO_POR_DEFECTO,
    ): Anotacion {
        exigirQueSePuedaEscribir(id, pagina)
        require(texto.isNotBlank()) { "No hay nada que escribir" }
        require(tamano > 0f) { "Una letra de tamaño $tamano no se vería" }

        return anotaciones.escribir(id, pagina, marco, texto, tamano).also { anotado(id) }
    }

    fun listar(
        id: IdDocumento,
        pagina: Int,
    ): List<Anotacion> = anotaciones.listar(id, pagina)

    /**
     * Quita una marca.
     *
     * Borrar también es escribir en el documento, así que un firmado tampoco lo
     * permite: quitarle una anotación a un PDF firmado rompe su firma igual que
     * ponérsela.
     */
    fun borrar(
        id: IdDocumento,
        pagina: Int,
        posicion: Int,
    ) {
        exigirQueSePuedaEscribir(id, pagina)
        anotaciones.borrar(id, pagina, posicion)
        anotado(id)
    }

    private fun exigirQueSePuedaEscribir(
        id: IdDocumento,
        pagina: Int,
    ) {
        val estado = registro.estado(id)
        if (estado.documento.estaFirmado) throw ErrorDocumento.DocumentoFirmado(id)
        require(pagina in 0 until estado.documento.paginas) {
            "La página $pagina no existe: «${estado.documento.nombre}» tiene ${estado.documento.paginas}"
        }
    }

    private fun anotado(id: IdDocumento) = registro.marcar(id, Marca.CAMBIOS_SIN_GUARDAR)

    private companion object {
        /** Doce puntos: el tamaño de un texto normal en un A4. */
        const val TAMANO_POR_DEFECTO = 12f
    }
}
