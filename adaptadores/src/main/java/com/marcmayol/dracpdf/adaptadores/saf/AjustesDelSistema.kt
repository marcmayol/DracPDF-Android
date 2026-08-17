package com.marcmayol.dracpdf.adaptadores.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Lo poco que una aplicación puede hacer sobre ser la predeterminada.
 *
 * **No puede declararse a sí misma.** Android reserva esa decisión al usuario, y con
 * razón: si bastara con pedirlo, la última aplicación instalada se quedaría siempre
 * con los PDF. Lo único posible es llevarle a la pantalla donde sí se decide y
 * decirle qué buscar allí.
 */
object AjustesDelSistema {
    private const val TIPO_PDF = "application/pdf"

    /**
     * Abre la ficha de la aplicación en los ajustes, donde vive «Abrir de forma
     * predeterminada».
     *
     * Es la única pantalla que existe en todas las versiones y capas: el atajo
     * concreto a los predeterminados cambia de nombre entre fabricantes, y uno que no
     * existe deja al usuario mirando un error del sistema.
     */
    fun abrirDeEstaAplicacion(contexto: Context): Boolean {
        val ficha =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", contexto.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        return runCatching { contexto.startActivity(ficha) }.isSuccess
    }

    /**
     * Si somos ya quien abre los PDF sin preguntar.
     *
     * Se resuelve un intent de verdad y se mira quién contesta. Cuando hay varias
     * aplicaciones y ninguna es la predeterminada, el sistema responde con su propio
     * selector —un paquete que no es ninguna de ellas—, y eso es exactamente lo que
     * hay que distinguir.
     */
    fun somosElLectorPorDefecto(contexto: Context): Boolean {
        val abrir =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://cualquiera/documento.pdf"), TIPO_PDF)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
        val quienContesta =
            contexto.packageManager
                .resolveActivity(abrir, 0)
                ?.activityInfo
                ?.packageName
        return quienContesta == contexto.packageName
    }
}
