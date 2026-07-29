package com.marcmayol.dracpdf.dominio

import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.Formulario
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.modelo.TipoCampo
import com.marcmayol.dracpdf.dominio.modelo.TipoFormulario
import com.marcmayol.dracpdf.dominio.puertos.FormService

/**
 * Doble del puerto de formularios. Como [RepositorioFalso], anota lo que se le pide:
 * que un XFA puro no se recorra página a página sólo se puede demostrar viendo que
 * nadie preguntó.
 */
class FormServiceFalso(
    private val tipo: TipoFormulario = TipoFormulario.ACROFORM,
    private val camposPorPagina: Map<Int, List<CampoFormulario>> = mapOf(0 to listOf(campoTexto(0, 0))),
) : FormService {
    val paginasConsultadas = mutableListOf<Int>()

    override fun formulario(id: IdDocumento): Formulario =
        Formulario(tipo = tipo, campos = camposPorPagina.values.sumOf { it.size })

    override fun camposDePagina(
        id: IdDocumento,
        pagina: Int,
    ): List<CampoFormulario> {
        paginasConsultadas += pagina
        return camposPorPagina[pagina].orEmpty()
    }

    companion object {
        fun campoTexto(
            pagina: Int,
            indice: Int,
            nombre: String = "campo$indice",
            valor: String = "",
        ) = CampoFormulario(
            nombre = nombre,
            pagina = pagina,
            indice = indice,
            tipo = TipoCampo.TEXTO,
            valor = valor,
            marco = RectPt(72f, 700f, 300f, 720f),
        )
    }
}
