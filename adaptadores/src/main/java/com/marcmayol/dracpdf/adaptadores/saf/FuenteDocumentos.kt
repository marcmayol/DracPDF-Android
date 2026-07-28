package com.marcmayol.dracpdf.adaptadores.saf

import android.content.ContentResolver
import android.net.Uri
import com.artifex.mupdf.fitz.SeekableInputStream
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.channels.FileChannel

/**
 * Convierte un [OrigenDocumento] del dominio en algo que MuPDF sepa leer. Existe
 * para que el repositorio no tenga que saber si detrás hay un `content://` del
 * sistema o un fichero del almacenamiento privado.
 */
interface FuenteDocumentos {
    fun abrir(origen: OrigenDocumento): SeekableInputStream
}

/** La fuente real: Storage Access Framework para lo externo, fichero para lo privado. */
class FuenteDocumentosAndroid(
    private val resolver: ContentResolver,
) : FuenteDocumentos {
    override fun abrir(origen: OrigenDocumento): SeekableInputStream =
        try {
            when (origen) {
                is OrigenDocumento.Externo -> FlujoSaf.de(resolver, Uri.parse(origen.identificador))
                is OrigenDocumento.Privado -> FlujoFichero(File(origen.identificador))
            }
        } catch (e: SecurityException) {
            // El permiso persistido ya no vale: el usuario lo revocó, o el proveedor
            // dejó de compartir. No es un PDF roto y no debe contarse como tal.
            throw ErrorDocumento.SinPermiso(origen).initCause(e) as ErrorDocumento
        } catch (e: IOException) {
            // No se ha llegado al fichero. Que el contenido sea o no un PDF es una
            // pregunta que aquí ni siquiera ha llegado a hacerse.
            throw ErrorDocumento.NoSePuedeAbrirElFichero(origen, e)
        }
}

/** Un fichero del almacenamiento privado, con la misma interfaz que el de SAF. */
private class FlujoFichero(
    fichero: File,
) : SeekableInputStream,
    AutoCloseable {
    private val canal: FileChannel = FileInputStream(fichero).channel

    override fun read(bytes: ByteArray): Int {
        val leidos = canal.read(java.nio.ByteBuffer.wrap(bytes))
        return if (leidos < 0) 0 else leidos
    }

    override fun seek(
        desplazamiento: Long,
        desde: Int,
    ): Long {
        val destino =
            when (desde) {
                com.artifex.mupdf.fitz.SeekableStream.SEEK_SET -> desplazamiento
                com.artifex.mupdf.fitz.SeekableStream.SEEK_CUR -> canal.position() + desplazamiento
                com.artifex.mupdf.fitz.SeekableStream.SEEK_END -> canal.size() + desplazamiento
                else -> throw IOException("Origen de posicionamiento desconocido: $desde")
            }
        canal.position(destino)
        return destino
    }

    override fun position(): Long = canal.position()

    override fun close() {
        runCatching { canal.close() }
    }
}
