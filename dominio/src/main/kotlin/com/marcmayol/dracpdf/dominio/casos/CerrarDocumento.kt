package com.marcmayol.dracpdf.dominio.casos

import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.puertos.DocumentRepository
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos

/**
 * Cierra un documento y lo saca del registro.
 *
 * El orden importa: primero se suelta el motor y luego el registro. Al revés queda
 * un documento nativo abierto al que ya nadie puede llegar para cerrarlo, y eso en
 * MuPDF es memoria fuera del montón de Java que el recolector no ve.
 */
class CerrarDocumento(
    private val repositorio: DocumentRepository,
    private val registro: RegistroDocumentos,
) {
    operator fun invoke(id: IdDocumento) {
        registro.estado(id) // Falla con NoEstaAbierto si el id no vale.
        repositorio.cerrar(id)
        registro.quitar(id)
    }
}
