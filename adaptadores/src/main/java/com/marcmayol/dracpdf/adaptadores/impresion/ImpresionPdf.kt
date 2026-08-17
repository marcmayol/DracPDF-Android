package com.marcmayol.dracpdf.adaptadores.impresion

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.File
import java.io.FileOutputStream

/**
 * Imprimir el documento por el sistema.
 *
 * Android no imprime PDF: **recoge un PDF** y se lo pasa al servicio de impresión que
 * elija el usuario, incluido «Guardar como PDF». Así que aquí no se rasteriza nada; lo
 * único que hay que hacer es entregar los bytes correctos.
 *
 * «Los correctos» incluye el rango: el cuadro de impresión del sistema deja elegir
 * «páginas 2-5», y quien tiene que obedecer eso es esta clase. Entregar el documento
 * entero cuando se pidieron tres páginas es el fallo silencioso clásico de este API,
 * porque el sistema no protesta: imprime de más.
 */
class ImpresionPdf(
    private val nombre: String,
    private val paginas: Int,
    /**
     * El fichero que hay que entregar. Recibe las páginas pedidas en base cero, o
     * `null` cuando se quiere el documento entero.
     */
    private val ficheroDe: (List<Int>?) -> File,
) : PrintDocumentAdapter() {
    override fun onLayout(
        anterior: PrintAttributes?,
        nuevos: PrintAttributes?,
        cancelacion: CancellationSignal?,
        respuesta: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancelacion?.isCanceled == true) {
            respuesta.onLayoutCancelled()
            return
        }
        val informacion =
            PrintDocumentInfo
                .Builder(nombre)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(paginas)
                .build()
        // El `true` dice que el contenido cambia con los atributos. Es mentira aquí
        // —el PDF es el mismo se imprima en A4 o en carta— pero decir `false` hace que
        // el sistema se salte `onWrite` cuando el usuario cambia de impresora, y
        // entonces imprime lo que había escrito para la anterior.
        respuesta.onLayoutFinished(informacion, true)
    }

    override fun onWrite(
        rangos: Array<out PageRange>?,
        destino: ParcelFileDescriptor,
        cancelacion: CancellationSignal?,
        respuesta: WriteResultCallback,
    ) {
        // En su propio hilo: el sistema llama a esto en el principal, y preparar el
        // PDF puede tardar lo que tarde el motor en escribir cincuenta páginas.
        Thread {
            try {
                val pedidas = paginasDe(rangos)
                val fichero = ficheroDe(pedidas)
                if (cancelacion?.isCanceled == true) {
                    respuesta.onWriteCancelled()
                    return@Thread
                }
                FileOutputStream(destino.fileDescriptor).use { salida ->
                    fichero.inputStream().use { it.copyTo(salida) }
                }
                // Se declara lo que **se ha escrito de verdad**: si se pidieron tres
                // páginas, el PDF entregado tiene tres y sus números empiezan en cero.
                val escritas = pedidas?.size ?: paginas
                respuesta.onWriteFinished(arrayOf(PageRange(0, escritas - 1)))
            } catch (e: java.io.IOException) {
                respuesta.onWriteFailed(e.message)
            }
        }.start()
    }

    /**
     * Las páginas que pide el sistema, en base cero. `null` significa «todas», que es
     * lo que permite entregar el fichero original sin tocarlo.
     *
     * Es pública para poder probarla: el resto de esta clase son llamadas del sistema
     * de impresión —con un `WriteResultCallback` que ni siquiera se puede construir
     * desde un test— y esta traducción es justo la parte donde un fallo no se nota,
     * porque imprimir de más no da ningún error.
     */
    fun paginasDe(rangos: Array<out PageRange>?): List<Int>? {
        if (rangos.isNullOrEmpty()) return null
        if (rangos.any { it == PageRange.ALL_PAGES }) return null
        val pedidas = rangos.flatMap { rango -> (rango.start..rango.end).toList() }.filter { it in 0 until paginas }
        return pedidas.takeIf { it.isNotEmpty() && it.size < paginas }
    }

    companion object {
        /** Abre el diálogo de impresión del sistema con este documento dentro. */
        fun lanzar(
            contexto: Context,
            adaptador: ImpresionPdf,
        ) {
            val servicio = contexto.getSystemService(Context.PRINT_SERVICE) as PrintManager
            servicio.print(adaptador.nombre, adaptador, null)
        }
    }
}
