package com.marcmayol.dracpdf.dominio.casos

import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.DocumentRepository
import com.marcmayol.dracpdf.dominio.registro.EstadoDocumento
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos

/**
 * Abre un documento y lo da de alta en el registro.
 *
 * El identificador de sesión se pide al registro antes de abrir, para que el
 * documento nazca ya con el nombre por el que lo van a conocer todos los demás.
 */
class AbrirDocumento(
    private val repositorio: DocumentRepository,
    private val registro: RegistroDocumentos,
) {
    operator fun invoke(
        origen: OrigenDocumento,
        contrasena: String? = null,
    ): EstadoDocumento {
        val id = registro.nuevoId()
        val documento = repositorio.abrir(id, origen, contrasena)
        return registro.registrar(documento)
    }
}
