package com.marcmayol.dracpdf.dominio.casos

import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.IdCampo
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.Marca
import com.marcmayol.dracpdf.dominio.modelo.TipoCampo
import com.marcmayol.dracpdf.dominio.puertos.FormService
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos

/**
 * Rellenar un campo del formulario.
 *
 * **El documento en memoria es la única fuente de verdad.** Aquí no hay borrador que
 * custodiar: se escribe en el documento y se vuelve a leer de él. Un buffer paralelo
 * con «lo que el usuario lleva escrito» sería una segunda verdad, y en un móvil,
 * donde el proceso puede morir en cualquier momento, la segunda verdad es la que se
 * pierde.
 *
 * Escribir marca el documento y **no lo guarda**: guardar es una decisión aparte,
 * porque escribir en el fichero cada tecla castigaría el almacenamiento y dejaría
 * revisiones sueltas a cada pulsación.
 */
class RellenarCampo(
    private val servicio: FormService,
    private val registro: RegistroDocumentos,
) {
    /** Escribe un campo de texto. */
    fun texto(
        id: IdDocumento,
        campo: IdCampo,
        valor: String,
    ): CampoFormulario =
        cambiar(id, campo, TIPOS_DE_TEXTO) {
            servicio.escribirTexto(id, campo, valor)
        }

    /** Marca o desmarca una casilla, o elige un botón de radio. */
    fun alternar(
        id: IdDocumento,
        campo: IdCampo,
    ): CampoFormulario =
        cambiar(id, campo, TIPOS_DE_MARCA) {
            servicio.alternar(id, campo)
        }

    /** Elige una opción de un combo o de una lista. */
    fun elegir(
        id: IdDocumento,
        campo: IdCampo,
        opcion: String,
    ): CampoFormulario =
        cambiar(id, campo, TIPOS_DE_ELECCION) { actual ->
            require(opcion in actual.opciones) {
                "«$opcion» no es una de las opciones de ${actual.nombre}: ${actual.opciones}"
            }
            servicio.elegirOpcion(id, campo, opcion)
        }

    /**
     * Lo común a los tres: comprobar que se puede tocar, tocarlo, y anotar que el
     * documento ya no coincide con su fichero.
     */
    private fun cambiar(
        id: IdDocumento,
        campo: IdCampo,
        tiposValidos: Set<TipoCampo>,
        accion: (CampoFormulario) -> CampoFormulario,
    ): CampoFormulario {
        val estado = registro.estado(id)
        if (estado.documento.estaFirmado) throw ErrorDocumento.DocumentoFirmado(id)

        val actual =
            servicio.camposDePagina(id, campo.pagina).find { it.id == campo }
                ?: throw IllegalArgumentException("El campo $campo no existe en «${estado.documento.nombre}»")

        require(actual.esEditable) {
            "El campo «${actual.nombre}» no se puede editar: lo bloqueó quien emitió el documento"
        }
        require(actual.tipo in tiposValidos) {
            "El campo «${actual.nombre}» es de tipo ${actual.tipo} y no admite este cambio"
        }

        val despues = accion(actual)
        registro.marcar(id, Marca.CAMBIOS_SIN_GUARDAR)
        return despues
    }

    private companion object {
        val TIPOS_DE_TEXTO = setOf(TipoCampo.TEXTO)
        val TIPOS_DE_MARCA = setOf(TipoCampo.CASILLA, TipoCampo.RADIO)

        /**
         * Un combo editable también acepta texto libre, pero eso llega cuando la
         * interfaz sepa ofrecer las dos cosas a la vez; por ahora, elegir.
         */
        val TIPOS_DE_ELECCION = setOf(TipoCampo.COMBO, TipoCampo.LISTA)
    }
}
