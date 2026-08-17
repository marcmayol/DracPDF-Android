package com.marcmayol.dracpdf.adaptadores.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Dónde acaba lo que produce una herramienta.
 *
 * Hay dos clases de destino y se comportan distinto: un fichero privado se escribe y
 * ya está, y uno del sistema hay que pedírselo al proveedor, que puede negarse, haber
 * caducado o no existir. Aquí se resuelven las dos por igual para que el adaptador de
 * MuPDF no tenga que saber de `content://`.
 */
class SalidasDeHerramienta(
    private val resolver: ContentResolver,
) {
    /**
     * Pone [temporal] en [destino] y lo deja escrito.
     *
     * En el destino privado es un renombrado —instantáneo y atómico— y en el externo
     * una copia por el flujo del proveedor, que es todo lo que el SAF permite: no hay
     * manera de renombrar dentro de un `content://` ajeno.
     */
    fun volcar(
        temporal: File,
        destino: OrigenDocumento,
    ) {
        when (destino) {
            is OrigenDocumento.Privado -> {
                val fichero = File(destino.identificador)
                fichero.parentFile?.mkdirs()
                if (!temporal.renameTo(fichero)) {
                    // Renombrar falla entre volúmenes distintos: la caché y el
                    // almacenamiento privado no siempre son el mismo.
                    temporal.copyTo(fichero, overwrite = true)
                }
            }

            is OrigenDocumento.Externo ->
                resolver.openOutputStream(Uri.parse(destino.identificador), "wt")?.use { salida ->
                    temporal.inputStream().use { it.copyTo(salida) }
                } ?: throw IOException("El sistema no deja escribir en ${destino.identificador}")
        }
    }

    /**
     * Crea un fichero llamado [nombre] dentro de [carpeta] y deja que [escritor] lo
     * llene.
     *
     * @return el origen del fichero creado, que no siempre se llama como se pidió: si
     *   ya había uno con ese nombre, el sistema añade un sufijo y hay que devolver el
     *   nombre de verdad, no el que se pretendía.
     */
    fun escribirEn(
        carpeta: OrigenDocumento,
        nombre: String,
        escritor: (OutputStream) -> Unit,
    ): OrigenDocumento =
        when (carpeta) {
            is OrigenDocumento.Privado -> {
                val destino = File(carpeta.identificador, nombre)
                destino.parentFile?.mkdirs()
                destino.outputStream().use(escritor)
                OrigenDocumento.Privado(destino.absolutePath, destino.name)
            }

            is OrigenDocumento.Externo -> {
                val arbol = Uri.parse(carpeta.identificador)
                val creado =
                    DocumentsContract.createDocument(resolver, arbol, tipoDe(nombre), nombre)
                        ?: throw IOException("No se ha podido crear «$nombre» en ${carpeta.identificador}")
                resolver.openOutputStream(creado)?.use(escritor)
                    ?: throw IOException("No se ha podido escribir «$nombre»")
                OrigenDocumento.Externo(creado.toString(), nombre)
            }
        }

    private fun tipoDe(nombre: String) =
        when (nombre.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            "html" -> "text/html"
            "md" -> "text/markdown"
            "csv" -> "text/csv"
            "rtf" -> "application/rtf"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> "application/octet-stream"
        }
}
