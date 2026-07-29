package com.marcmayol.dracpdf.adaptadores.saf

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.artifex.mupdf.fitz.SeekableInputOutputStream
import com.artifex.mupdf.fitz.SeekableStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * El fichero del documento abierto para leer **y** escribir, que es lo que exige el
 * guardado incremental: MuPDF necesita releer lo que ya hay para poder añadir una
 * revisión detrás sin tocarlo.
 *
 * El modo del descriptor es `"rw"` y no `"w"` a conciencia. `"w"` en el Storage
 * Access Framework trunca el fichero al abrirlo: el documento se quedaría en cero
 * bytes antes de que MuPDF hubiera escrito una sola línea, y con él se irían las
 * revisiones anteriores y las firmas que colgaran de ellas.
 */
sealed class FlujoEscritura :
    SeekableInputOutputStream,
    AutoCloseable {
    companion object {
        /**
         * Abre un `content://` para leer y escribir.
         *
         * @throws IOException si el proveedor no deja escribir. Pasa con los URI
         *   efímeros que comparten WhatsApp o Gmail, que son de sólo lectura por
         *   diseño; ahí lo que corresponde no es insistir, sino ofrecer guardar una
         *   copia, y de eso se encarga la Fase 11.
         */
        fun de(
            resolver: ContentResolver,
            uri: Uri,
        ): FlujoEscritura {
            val descriptor =
                resolver.openFileDescriptor(uri, "rw")
                    ?: throw IOException("El proveedor no ha entregado descriptor de escritura para $uri")
            return FlujoEscrituraSaf(descriptor)
        }

        fun de(fichero: File): FlujoEscritura = FlujoEscrituraFichero(fichero)
    }
}

/**
 * Sobre un descriptor del sistema.
 *
 * Los dos flujos salen **del mismo descriptor** y por eso comparten la posición del
 * fichero: el kernel guarda un solo desplazamiento por descriptor abierto, así que
 * leer avanza el punto donde escribirá el siguiente `write`, que es justo lo que
 * espera quien pide un flujo de lectura y escritura. Con dos descriptores distintos
 * cada uno llevaría su cuenta y la revisión acabaría escrita en mitad del fichero.
 */
private class FlujoEscrituraSaf(
    private val descriptor: ParcelFileDescriptor,
) : FlujoEscritura() {
    private val entrada = FileInputStream(descriptor.fileDescriptor)
    private val salida = FileOutputStream(descriptor.fileDescriptor)

    override fun read(bytes: ByteArray): Int {
        val leidos = entrada.channel.read(ByteBuffer.wrap(bytes))
        return if (leidos < 0) 0 else leidos
    }

    override fun write(
        bytes: ByteArray,
        desde: Int,
        cuantos: Int,
    ) {
        salida.write(bytes, desde, cuantos)
    }

    override fun truncate() {
        entrada.channel.truncate(entrada.channel.position())
    }

    override fun seek(
        desplazamiento: Long,
        desde: Int,
    ): Long {
        val destino = destinoDe(desplazamiento, desde, entrada.channel.position(), entrada.channel.size())
        entrada.channel.position(destino)
        return destino
    }

    override fun position(): Long = entrada.channel.position()

    override fun close() {
        runCatching { salida.flush() }
        runCatching { descriptor.close() }
    }
}

/** Sobre un fichero del almacenamiento privado. */
private class FlujoEscrituraFichero(
    fichero: File,
) : FlujoEscritura() {
    private val acceso = RandomAccessFile(fichero, "rw")

    override fun read(bytes: ByteArray): Int {
        val leidos = acceso.read(bytes)
        return if (leidos < 0) 0 else leidos
    }

    override fun write(
        bytes: ByteArray,
        desde: Int,
        cuantos: Int,
    ) {
        acceso.write(bytes, desde, cuantos)
    }

    override fun truncate() {
        acceso.setLength(acceso.filePointer)
    }

    override fun seek(
        desplazamiento: Long,
        desde: Int,
    ): Long {
        val destino = destinoDe(desplazamiento, desde, acceso.filePointer, acceso.length())
        acceso.seek(destino)
        return destino
    }

    override fun position(): Long = acceso.filePointer

    override fun close() {
        runCatching { acceso.close() }
    }
}

private fun destinoDe(
    desplazamiento: Long,
    desde: Int,
    actual: Long,
    tamano: Long,
): Long =
    when (desde) {
        SeekableStream.SEEK_SET -> desplazamiento
        SeekableStream.SEEK_CUR -> actual + desplazamiento
        SeekableStream.SEEK_END -> tamano + desplazamiento
        else -> throw IOException("Origen de posicionamiento desconocido: $desde")
    }
