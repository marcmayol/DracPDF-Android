package com.marcmayol.dracpdf.dominio

import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.Formulario
import com.marcmayol.dracpdf.dominio.modelo.IdCampo
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
    camposPorPagina: Map<Int, List<CampoFormulario>> = mapOf(0 to listOf(campoTexto(0, 0))),
) : FormService {
    val paginasConsultadas = mutableListOf<Int>()

    /** Lo que se ha escrito, en orden, para poder comprobar qué llegó al motor. */
    val escrituras = mutableListOf<Pair<IdCampo, String>>()

    private val campos = camposPorPagina.mapValues { (_, lista) -> lista.toMutableList() }.toMutableMap()

    override fun formulario(id: IdDocumento): Formulario =
        Formulario(tipo = tipo, campos = campos.values.sumOf { it.size })

    override fun camposDePagina(
        id: IdDocumento,
        pagina: Int,
    ): List<CampoFormulario> {
        paginasConsultadas += pagina
        return campos[pagina].orEmpty()
    }

    override fun escribirTexto(
        id: IdDocumento,
        campo: IdCampo,
        valor: String,
    ): CampoFormulario {
        escrituras += campo to valor
        return cambiar(campo) { it.copy(valor = valor) }
    }

    override fun alternar(
        id: IdDocumento,
        campo: IdCampo,
    ): CampoFormulario {
        escrituras += campo to "alternar"
        return cambiar(campo) {
            if (it.marcado) {
                it.copy(valor = CampoFormulario.APAGADO, marcado = false)
            } else {
                it.copy(valor = "Si", marcado = true)
            }
        }
    }

    override fun elegirOpcion(
        id: IdDocumento,
        campo: IdCampo,
        opcion: String,
    ): CampoFormulario {
        escrituras += campo to opcion
        return cambiar(campo) { it.copy(valor = opcion) }
    }

    private fun cambiar(
        campo: IdCampo,
        como: (CampoFormulario) -> CampoFormulario,
    ): CampoFormulario {
        val lista = campos.getValue(campo.pagina)
        val donde = lista.indexOfFirst { it.id == campo }
        val nuevo = como(lista[donde])
        lista[donde] = nuevo
        return nuevo
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
