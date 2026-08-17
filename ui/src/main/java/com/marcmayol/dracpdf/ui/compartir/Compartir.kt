package com.marcmayol.dracpdf.ui.compartir

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento

/**
 * Mandar cosas fuera de la aplicación por el menú de compartir del sistema.
 *
 * Todo pasa por `ACTION_SEND` y por el selector del sistema: aquí no se decide a qué
 * aplicación va nada. Una lista propia de destinos sería una lista que envejece, y el
 * usuario ya tiene la suya.
 */
object Compartir {
    /** El texto seleccionado, a donde el usuario quiera. */
    fun texto(
        contexto: Context,
        texto: String,
    ) {
        if (texto.isBlank()) return
        val envio =
            Intent(Intent.ACTION_SEND).apply {
                type = TIPO_TEXTO
                putExtra(Intent.EXTRA_TEXT, texto)
            }
        contexto.startActivity(Intent.createChooser(envio, "Compartir el texto"))
    }

    /**
     * El documento entero.
     *
     * Sólo se puede compartir lo que tiene un URI que otras aplicaciones puedan leer,
     * es decir, lo que vino del sistema. Un fichero del almacenamiento privado no se
     * comparte tal cual —nadie más puede abrirlo— y devolver `false` deja que quien
     * llama lo diga en vez de abrir un selector que no llevaría a ninguna parte.
     */
    fun documento(
        contexto: Context,
        origen: OrigenDocumento,
        nombre: String,
    ): Boolean {
        if (origen !is OrigenDocumento.Externo) return false
        val envio =
            Intent(Intent.ACTION_SEND).apply {
                type = TIPO_PDF
                putExtra(Intent.EXTRA_STREAM, Uri.parse(origen.identificador))
                putExtra(Intent.EXTRA_TITLE, nombre)
                // Sin esto, la aplicación de destino recibe un URI que no tiene permiso
                // para abrir: el documento llega y no se ve.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        contexto.startActivity(Intent.createChooser(envio, "Compartir «$nombre»"))
        return true
    }

    /**
     * Abre un enlace del documento fuera de la aplicación.
     *
     * Se comprueba que haya algo que lo abra antes de intentarlo: un `mailto:` sin
     * cliente de correo instalado, o un esquema raro metido en el PDF, tiraría la
     * aplicación con `ActivityNotFoundException` al tocar un enlace.
     */
    fun abrirEnlace(
        contexto: Context,
        url: String,
    ): Boolean {
        val ir = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { contexto.startActivity(ir) }.isSuccess
    }

    private const val TIPO_TEXTO = "text/plain"
    private const val TIPO_PDF = "application/pdf"
}
