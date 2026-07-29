package com.marcmayol.dracpdf.dominio.casos

import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.Formulario
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.TipoFormulario
import com.marcmayol.dracpdf.dominio.puertos.FormService
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos

/**
 * Qué formulario trae un documento y qué campos hay en cada página.
 *
 * El rango de página se valida aquí por el mismo motivo que en [RenderizarPagina]:
 * un índice fuera de rango no debe llegar nunca a la biblioteca nativa.
 *
 * Un XFA puro no se recorre: no tiene campos AcroForm que enseñar, y preguntar por
 * ellos página a página sólo devolvería listas vacías haciendo creer que el
 * documento no tiene formulario, cuando lo que pasa es que no se puede rellenar.
 */
class ListarCampos(
    private val servicio: FormService,
    private val registro: RegistroDocumentos,
) {
    operator fun invoke(
        id: IdDocumento,
        pagina: Int,
    ): List<CampoFormulario> {
        val estado = registro.estado(id)
        val paginas = estado.documento.paginas
        require(pagina in 0 until paginas) {
            "La página $pagina no existe: «${estado.documento.nombre}» tiene $paginas"
        }
        if (servicio.formulario(id).tipo == TipoFormulario.XFA_PURO) return emptyList()
        return servicio.camposDePagina(id, pagina)
    }

    /** El formulario del documento, para decidir si hay overlay y si hay que avisar. */
    fun formulario(id: IdDocumento): Formulario {
        registro.estado(id)
        return servicio.formulario(id)
    }
}
