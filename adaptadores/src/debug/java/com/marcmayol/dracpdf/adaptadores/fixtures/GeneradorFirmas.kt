package com.marcmayol.dracpdf.adaptadores.fixtures

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream

/**
 * Fabrica el PNG de una firma de prueba: un trazo oscuro sobre **fondo
 * transparente**.
 *
 * Lo transparente es el punto entero del fixture. Con un fondo blanco todos los
 * tests pasarían igual y la firma taparía el documento en cuanto se estampara encima
 * de una línea, que es justo lo que hay que evitar y lo que sólo se ve al mirar los
 * píxeles de alrededor del trazo.
 */
object GeneradorFirmas {
    /** Ancho y alto del PNG de prueba, en píxeles. */
    const val ANCHO = 240
    const val ALTO = 80

    /**
     * Un trazo diagonal grueso que cruza la imagen, más una curva: ocupa parte del
     * lienzo y deja el resto transparente, que es lo que permite comprobar las dos
     * cosas a la vez.
     */
    fun png(): ByteArray {
        val mapa = Bitmap.createBitmap(ANCHO, ALTO, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(mapa)
        // Sin pintar el fondo: nace transparente y así se queda donde no haya tinta.
        val pincel =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(17, 19, 24)
                style = Paint.Style.STROKE
                strokeWidth = 6f
                strokeCap = Paint.Cap.ROUND
            }
        lienzo.drawLine(20f, ALTO - 20f, ANCHO - 40f, 20f, pincel)
        lienzo.drawCircle(ANCHO - 30f, ALTO / 2f, 14f, pincel)

        return ByteArrayOutputStream().use { salida ->
            mapa.compress(Bitmap.CompressFormat.PNG, 100, salida)
            mapa.recycle()
            salida.toByteArray()
        }
    }
}
