package com.marcmayol.dracpdf.dominio.casos

import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.Marca
import com.marcmayol.dracpdf.dominio.puertos.DocumentRepository
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos

/**
 * Escribe en el fichero lo que se ha cambiado en el documento.
 *
 * La marca sólo se quita **después** de que el motor haya escrito. Si el guardado
 * falla —el permiso del SAF caducó, el proveedor se cayó, no queda espacio—, la
 * excepción sale y el documento sigue marcado como sin guardar, que es la verdad. Un
 * «guardado» que limpia la marca antes de tiempo es la forma más limpia de perder el
 * trabajo de alguien sin que se entere.
 */
class GuardarDocumento(
    private val repositorio: DocumentRepository,
    private val registro: RegistroDocumentos,
) {
    /**
     * @return `true` si había algo que guardar y se guardó; `false` si no había nada,
     *   que es distinto de haber fallado.
     */
    operator fun invoke(id: IdDocumento): Boolean {
        registro.estado(id)
        if (!repositorio.tieneCambiosSinGuardar(id)) {
            // El registro puede creer que hay cambios porque alguien tocó un campo y
            // volvió a dejarlo como estaba. Quien tiene la última palabra es el motor.
            registro.desmarcar(id, Marca.CAMBIOS_SIN_GUARDAR)
            return false
        }
        repositorio.guardarIncremental(id)
        registro.desmarcar(id, Marca.CAMBIOS_SIN_GUARDAR)
        return true
    }
}
