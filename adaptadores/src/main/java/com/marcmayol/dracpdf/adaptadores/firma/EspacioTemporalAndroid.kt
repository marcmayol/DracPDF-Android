package com.marcmayol.dracpdf.adaptadores.firma

import com.artifex.mupdf.fitz.SeekableStream
import com.marcmayol.dracpdf.adaptadores.saf.FlujoEscritura
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentos
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.EspacioTemporal
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * El espacio de trabajo: la caché privada de la aplicación.
 *
 * Los temporales son siempre **privados**, nunca del sistema de ficheros compartido,
 * y da igual de dónde venga el documento: un PDF a medio firmar no tiene por qué
 * aparecer en la galería de nadie, y la caché privada la limpia Android sola si hace
 * falta espacio.
 */
class EspacioTemporalAndroid(
    private val carpeta: File,
    private val fuente: FuenteDocumentos,
) : EspacioTemporal {
    override fun nuevo(nombreParecidoA: String): OrigenDocumento {
        carpeta.mkdirs()
        val fichero = File(carpeta, "${System.nanoTime()}-$nombreParecidoA")
        return OrigenDocumento.Privado(fichero.absolutePath, fichero.name)
    }

    /**
     * Pone el temporal en el sitio del destino.
     *
     * Con un fichero privado se usa `renameTo`, que el sistema hace de una vez. Con un
     * `content://` no hay renombrado posible —el destino lo gobierna otra aplicación—,
     * así que se copia el contenido dentro. Ahí la atomicidad la da el proveedor y no
     * nosotros: es la limitación real del Storage Access Framework, y conviene saberlo
     * en lugar de creer que se ha garantizado algo que no.
     */
    override fun reemplazar(
        destino: OrigenDocumento,
        temporal: OrigenDocumento,
    ) {
        val origen = File(temporal.identificador)
        if (!origen.exists()) throw IOException("El temporal ${temporal.identificador} no existe")

        when (destino) {
            is OrigenDocumento.Privado -> {
                val ficheroDestino = File(destino.identificador)
                if (!origen.renameTo(ficheroDestino)) {
                    // Cruzar sistemas de ficheros hace fallar el renombrado; entonces se
                    // copia, que es lo único que queda.
                    origen.copyTo(ficheroDestino, overwrite = true)
                    origen.delete()
                }
            }

            is OrigenDocumento.Externo -> volcarPorSaf(origen, destino)
        }
    }

    private fun volcarPorSaf(
        origen: File,
        destino: OrigenDocumento,
    ) {
        fuente.abrirParaEscribir(destino).use { salida ->
            salida.seek(0, SeekableStream.SEEK_SET)
            origen.inputStream().use { entrada -> copiar(entrada, salida) }
            // El documento firmado puede ser más corto que el que había —no es lo
            // normal, pero puede—, y entonces hay que cortar la cola.
            salida.truncate()
        }
    }

    private fun copiar(
        entrada: InputStream,
        salida: FlujoEscritura,
    ) {
        val bufer = ByteArray(TAMANO_BUFER)
        while (true) {
            val leidos = entrada.read(bufer)
            if (leidos <= 0) break
            salida.write(bufer, 0, leidos)
        }
    }

    override fun borrar(temporal: OrigenDocumento) {
        runCatching { File(temporal.identificador).delete() }
    }

    private companion object {
        const val TAMANO_BUFER = 64 * 1024
    }
}
