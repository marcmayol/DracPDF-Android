package com.marcmayol.dracpdf.dominio.casos

import com.marcmayol.dracpdf.dominio.modelo.Credencial
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.Marca
import com.marcmayol.dracpdf.dominio.modelo.SelloVisible
import com.marcmayol.dracpdf.dominio.puertos.DocumentRepository
import com.marcmayol.dracpdf.dominio.puertos.EspacioTemporal
import com.marcmayol.dracpdf.dominio.puertos.SignatureService
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos

/**
 * Firmar digitalmente el documento que se está viendo.
 *
 * Los pasos van en este orden y no en otro, y cada uno está donde está por un motivo:
 *
 * 1. **Volcar lo que haya en memoria.** Si quedan campos rellenos sin guardar, hay
 *    que escribirlos antes: lo que se firma son los bytes del fichero, así que firmar
 *    con cambios pendientes dejaría una firma sobre un documento que el usuario no
 *    reconocería al reabrirlo.
 * 2. **Cerrar el documento.** El que firma necesita leer el fichero entero y
 *    escribirlo en otro; con el motor teniéndolo abierto, en Windows no se puede
 *    reemplazar y en cualquier sistema es pedirle problemas.
 * 3. **Firmar a un temporal**, no encima. Si algo se rompe a mitad, el documento del
 *    usuario sigue intacto.
 * 4. **Reemplazar de una vez** y volver a abrir bajo el **mismo identificador**: para
 *    quien está mirando la pantalla, sigue siendo el mismo documento, no uno nuevo.
 * 5. **Marcar FIRMADO**, que es lo que a partir de ahora rechaza la edición.
 *
 * Si algo falla, se deshace lo que se pueda y el documento se reabre igualmente: lo
 * que no puede pasar es que un error dejando la aplicación sin documento abierto.
 */
class FirmarDocumento(
    private val servicio: SignatureService,
    private val repositorio: DocumentRepository,
    private val temporales: EspacioTemporal,
    private val registro: RegistroDocumentos,
    private val guardar: GuardarDocumento,
) {
    operator fun invoke(
        id: IdDocumento,
        credencial: Credencial,
        sello: SelloVisible?,
    ) {
        val estado = registro.estado(id)
        // Firmar dos veces es legítimo —un documento puede llevar varias firmas—, así
        // que aquí no se rechaza estar ya firmado. Lo que se rechaza es editar, y eso
        // lo miran los casos de uso que editan.
        val origen = repositorio.origenDe(id)

        guardar(id)

        val temporal = temporales.nuevo(estado.documento.nombre)
        var firmado = false
        try {
            repositorio.cerrar(id)
            servicio.firmar(origen, temporal, credencial, sello)
            temporales.reemplazar(origen, temporal)
            firmado = true
        } finally {
            temporales.borrar(temporal)
            // Pase lo que pase, el documento vuelve a estar abierto y visible: dejar
            // al usuario mirando una pantalla vacía porque la firma falló sería
            // castigarle dos veces por el mismo error.
            repositorio.abrir(id, origen)
            registro.registrar(
                estado.documento.copy(
                    marcas = if (firmado) estado.documento.marcas + Marca.FIRMADO else estado.documento.marcas,
                ),
            )
        }
    }

    /** Las firmas que tiene el fichero ahora mismo, leídas del disco. */
    fun firmasDe(id: IdDocumento) = servicio.verificar(repositorio.origenDe(id))

    /**
     * Comprueba si el documento nace firmado y lo marca.
     *
     * Se llama al abrir, y hace falta para los documentos firmados por **terceros**:
     * llegan firmados de fuera, nadie de aquí los ha firmado, y hay que tratarlos con
     * el mismo cuidado que los propios.
     */
    fun marcarSiEstaFirmado(id: IdDocumento) {
        val estado = registro.estado(id)
        if (estado.documento.estaFirmado) return
        val firmas = runCatching { servicio.verificar(repositorio.origenDe(id)) }.getOrDefault(emptyList())
        if (firmas.isNotEmpty()) registro.marcar(id, Marca.FIRMADO)
    }

    /**
     * Guarda una copia editable de un documento firmado.
     *
     * Es la salida que el escritorio ofrece y que aquí se hereda: quien intenta editar
     * algo firmado casi nunca quiere romper la firma, quiere seguir trabajando. La
     * copia no se firma y no lleva la marca, así que se puede editar sin tocar el
     * original.
     */
    fun copiaEditable(
        id: IdDocumento,
        destino: com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento,
    ) {
        registro.estado(id)
        repositorio.copiarA(id, destino)
    }
}
