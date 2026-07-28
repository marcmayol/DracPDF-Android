package com.marcmayol.dracpdf.adaptadores.saf

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento

/**
 * Traduce lo que entrega el sistema —un URI del selector, un intent de ver, un
 * envío desde otra aplicación— en el [OrigenDocumento] que entiende el dominio.
 */
object OrigenesDelSistema {
    private const val NOMBRE_POR_DEFECTO = "documento.pdf"

    /** El origen de un URI, con el nombre que le dé su proveedor. */
    fun de(
        resolver: ContentResolver,
        uri: Uri,
    ): OrigenDocumento = OrigenDocumento.Externo(uri.toString(), nombreDe(resolver, uri))

    /**
     * El documento que trae un intent, si lo trae: `VIEW` de otra aplicación (el
     * explorador de archivos, el correo) o `SEND` del menú de compartir.
     */
    fun delIntent(
        resolver: ContentResolver,
        intent: Intent?,
    ): OrigenDocumento? {
        val uri =
            when (intent?.action) {
                Intent.ACTION_VIEW -> intent.data
                Intent.ACTION_SEND -> intent.extraStream()
                else -> null
            } ?: return null
        return de(resolver, uri)
    }

    @Suppress("DEPRECATION")
    private fun Intent.extraStream(): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    /**
     * El nombre visible del documento.
     *
     * Se le pregunta al proveedor en vez de sacarlo del URI: un `content://` de
     * Drive o de Gmail no lleva el nombre dentro, y el último segmento sería un
     * número. Si el proveedor no contesta, un nombre genérico antes que enseñar
     * basura.
     */
    fun nombreDe(
        resolver: ContentResolver,
        uri: Uri,
    ): String =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val columna = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columna >= 0 && cursor.moveToFirst()) cursor.getString(columna) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: NOMBRE_POR_DEFECTO

    /**
     * Pide quedarse con el permiso de lectura para siempre.
     *
     * Sin esto, el documento deja de poder abrirse en cuanto el proceso muere, y la
     * lista de recientes de la Fase 7 sería una lista de enlaces rotos. No todos los
     * proveedores lo conceden, así que fallar aquí no puede impedir abrir ahora.
     */
    fun recordarPermiso(
        resolver: ContentResolver,
        uri: Uri,
    ): Boolean =
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        }.getOrElse { false }
}
