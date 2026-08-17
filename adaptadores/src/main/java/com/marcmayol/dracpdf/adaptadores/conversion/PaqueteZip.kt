package com.marcmayol.dracpdf.adaptadores.conversion

import java.io.Closeable
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Un ZIP con XML dentro, que es lo que son un ODT y un XLSX.
 *
 * Los dos formatos se escriben a mano y sin dependencias nuevas —es la vía que el plan
 * decidió— y los dos necesitan lo mismo, así que la mecánica del paquete vive aquí una
 * sola vez en vez de repetirse en cada escritor.
 *
 * **La entrada sin comprimir no es un capricho de rendimiento.** OpenDocument exige que
 * `mimetype` sea la primera entrada del paquete y que vaya almacenada tal cual: es lo que
 * permite reconocer el tipo de fichero leyendo los primeros bytes, sin descomprimir nada.
 * Un ODT con el `mimetype` deflactado abre igual en LibreOffice y no lo reconoce ni el
 * gestor de archivos ni el sistema.
 */
internal class PaqueteZip(
    salida: OutputStream,
) : Closeable {
    private val zip = ZipOutputStream(salida)

    /**
     * Guarda [contenido] tal cual, sin comprimir.
     *
     * Con el método `STORED` hay que decirle el tamaño y el CRC por adelantado: nadie los
     * calcula por ti porque no hay compresor que los vaya midiendo de paso.
     */
    fun crudo(
        nombre: String,
        contenido: String,
    ) {
        val bytes = contenido.toByteArray(Charsets.UTF_8)
        val entrada =
            ZipEntry(nombre).apply {
                method = ZipEntry.STORED
                size = bytes.size.toLong()
                compressedSize = bytes.size.toLong()
                crc = CRC32().apply { update(bytes) }.value
            }
        escribir(entrada, bytes)
    }

    fun comprimido(
        nombre: String,
        contenido: String,
    ) {
        escribir(ZipEntry(nombre).apply { method = ZipEntry.DEFLATED }, contenido.toByteArray(Charsets.UTF_8))
    }

    private fun escribir(
        entrada: ZipEntry,
        bytes: ByteArray,
    ) {
        zip.putNextEntry(entrada)
        zip.write(bytes)
        zip.closeEntry()
    }

    /** Cierra el paquete. No cierra el flujo de destino: eso es de quien lo abrió. */
    override fun close() {
        zip.finish()
        zip.flush()
    }
}
