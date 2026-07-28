package com.marcmayol.dracpdf.adaptadores.saf

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.artifex.mupdf.fitz.SeekableInputStream
import com.artifex.mupdf.fitz.SeekableStream
import java.io.FileInputStream
import java.io.IOException
import java.nio.channels.FileChannel

/**
 * Un documento del Storage Access Framework, leído **sin copiarlo**.
 *
 * MuPDF no necesita el fichero entero en memoria ni en disco: le basta con poder
 * saltar por él, y para eso acepta un [SeekableInputStream]. Un PDF de 300 MB en el
 * móvil se abre igual de rápido que uno de 300 KB porque sólo se leen las partes que
 * hacen falta —el índice de objetos del final, y las páginas que se miran—.
 *
 * Copiarlo a la caché privada sería más simple y sería un error: duplicaría el
 * espacio ocupado y metería una espera de varios segundos antes de ver la primera
 * página.
 */
class FlujoSaf private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val canal: FileChannel,
) : SeekableInputStream,
    AutoCloseable {
    override fun read(bytes: ByteArray): Int {
        val leidos = canal.read(java.nio.ByteBuffer.wrap(bytes))
        // MuPDF espera 0 al final del fichero; los canales de Java devuelven -1.
        return if (leidos < 0) 0 else leidos
    }

    override fun seek(
        desplazamiento: Long,
        desde: Int,
    ): Long {
        val destino =
            when (desde) {
                SeekableStream.SEEK_SET -> desplazamiento
                SeekableStream.SEEK_CUR -> canal.position() + desplazamiento
                SeekableStream.SEEK_END -> canal.size() + desplazamiento
                else -> throw IOException("Origen de posicionamiento desconocido: $desde")
            }
        canal.position(destino)
        return destino
    }

    override fun position(): Long = canal.position()

    override fun close() {
        // El canal y el descriptor se cierran en este orden porque el canal sale del
        // descriptor: al revés, cerrar el canal tocaría un descriptor ya cerrado.
        runCatching { canal.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        /**
         * Abre un `content://` (o cualquier URI que el sistema sepa resolver) en modo
         * lectura.
         *
         * @throws IOException si el proveedor ya no concede acceso: pasa con los
         *   recientes cuyo permiso se revocó, y hay que distinguirlo de un PDF roto.
         */
        fun de(
            resolver: ContentResolver,
            uri: Uri,
        ): FlujoSaf {
            val descriptor =
                resolver.openFileDescriptor(uri, "r")
                    ?: throw IOException("El proveedor no ha entregado descriptor para $uri")
            val canal = FileInputStream(descriptor.fileDescriptor).channel
            return FlujoSaf(descriptor, canal)
        }
    }
}
