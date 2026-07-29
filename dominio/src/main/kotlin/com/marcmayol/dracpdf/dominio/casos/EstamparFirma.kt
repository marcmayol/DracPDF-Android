package com.marcmayol.dracpdf.dominio.casos

import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.Estampado
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.IdFirma
import com.marcmayol.dracpdf.dominio.modelo.Marca
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.puertos.AlmacenFirmas
import com.marcmayol.dracpdf.dominio.puertos.DocumentRepository
import com.marcmayol.dracpdf.dominio.puertos.StampService
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos

/**
 * Pone una firma de la biblioteca sobre una página.
 *
 * Como rellenar un campo, esto **no guarda**: deja el documento marcado y guardar
 * sigue siendo una decisión aparte. Y como rellenar un campo, un documento firmado
 * lo rechaza: estampar encima de una firma digital la invalidaría, y quien lo
 * intenta quiere seguir trabajando, no romperla.
 */
class EstamparFirma(
    private val servicio: StampService,
    private val firmas: AlmacenFirmas,
    private val repositorio: DocumentRepository,
    private val registro: RegistroDocumentos,
) {
    operator fun invoke(
        id: IdDocumento,
        firma: IdFirma,
        pagina: Int,
        marco: RectPt,
    ): Estampado {
        val estado = registro.estado(id)
        if (estado.documento.estaFirmado) throw ErrorDocumento.DocumentoFirmado(id)

        require(pagina in 0 until estado.documento.paginas) {
            "La página $pagina no existe: «${estado.documento.nombre}» tiene ${estado.documento.paginas}"
        }
        require(marco.ancho > 0f && marco.alto > 0f) {
            "Una firma no puede medir cero: ${marco.ancho} x ${marco.alto}"
        }

        val tamano = repositorio.tamanoPagina(id, pagina)
        require(cabeEn(marco, tamano.ancho, tamano.alto)) {
            "La firma se sale de la página: $marco frente a ${tamano.ancho} x ${tamano.alto}"
        }

        val estampado = servicio.estampar(id, pagina, marco, firmas.leer(firma))
        registro.marcar(id, Marca.CAMBIOS_SIN_GUARDAR)
        return estampado
    }

    /**
     * Que el marco quepa se comprueba aquí y no en la interfaz.
     *
     * Una firma medio fuera de la página no da error en ningún sitio: se guarda tan
     * ricamente y aparece cortada al abrir el documento en cualquier otro visor, que
     * es de las cosas que sólo se descubren cuando ya se ha enviado.
     */
    private fun cabeEn(
        marco: RectPt,
        ancho: Float,
        alto: Float,
    ): Boolean =
        marco.x0 >= -TOLERANCIA_PT &&
            marco.y0 >= -TOLERANCIA_PT &&
            marco.x1 <= ancho + TOLERANCIA_PT &&
            marco.y1 <= alto + TOLERANCIA_PT

    private companion object {
        /** Un punto de margen: el redondeo del arrastre no debe costar un error. */
        const val TOLERANCIA_PT = 1f
    }
}
