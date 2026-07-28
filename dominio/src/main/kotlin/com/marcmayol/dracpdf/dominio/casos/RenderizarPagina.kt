package com.marcmayol.dracpdf.dominio.casos

import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.PaginaRenderizada
import com.marcmayol.dracpdf.dominio.modelo.TamanoPt
import com.marcmayol.dracpdf.dominio.puertos.DocumentRepository
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos

/**
 * Rasteriza una página a una escala.
 *
 * El rango de página se valida **aquí**, en el dominio, y no en la interfaz ni en el
 * adaptador: pedirle a MuPDF la página 900 de un documento de 12 es pasarle un
 * índice fuera de rango a una biblioteca nativa, que no es un sitio donde uno quiera
 * enterarse de sus errores.
 */
class RenderizarPagina(
    private val repositorio: DocumentRepository,
    private val registro: RegistroDocumentos,
) {
    operator fun invoke(
        id: IdDocumento,
        pagina: Int,
        escala: Float,
    ): PaginaRenderizada {
        validar(id, pagina, escala)
        return repositorio.renderizar(id, pagina, escala)
    }

    /** Tamaño de la página sin rasterizarla, con la misma validación. */
    fun tamano(
        id: IdDocumento,
        pagina: Int,
    ): TamanoPt {
        validar(id, pagina, escala = 1f)
        return repositorio.tamanoPagina(id, pagina)
    }

    private fun validar(
        id: IdDocumento,
        pagina: Int,
        escala: Float,
    ) {
        val estado = registro.estado(id)
        val paginas = estado.documento.paginas
        require(pagina in 0 until paginas) {
            "La página $pagina no existe: «${estado.documento.nombre}» tiene $paginas"
        }
        require(escala > 0f) { "La escala tiene que ser positiva, y era $escala" }
        require(escala <= ESCALA_MAXIMA) {
            "La escala $escala pasa de $ESCALA_MAXIMA: un render así no cabe en memoria"
        }
    }

    companion object {
        /**
         * Techo de escala. Una A4 a 8x son unos 420 MB en ARGB: no es un zoom, es un
         * cierre por falta de memoria. El zoom del visor pasa de aquí escalando el
         * bitmap ya dibujado, no pidiendo un render más grande.
         */
        const val ESCALA_MAXIMA = 8f
    }
}
