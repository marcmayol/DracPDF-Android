package com.marcmayol.dracpdf.adaptadores.camara

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import java.io.File
import java.io.IOException

/**
 * La foto de una hoja, recortada y puesta de frente.
 *
 * La cuenta la hace [homografiaDe], que es Kotlin puro y se prueba sin dispositivo; aquí
 * sólo se aplica. La separación no es ceremonia: la parte que se puede equivocar es la
 * geometría —qué esquina es cuál, qué matriz sale— y ésa conviene poder probarla sin
 * emulador, mientras que pasar píxeles por una matriz ya lo hace bien el sistema.
 *
 * **Lo hace el `Canvas` y no un bucle propio.** Android sabe dibujar un mapa de bits a
 * través de una matriz con perspectiva, con filtrado e interpolación, y lo hace en la
 * GPU; recorrer cinco millones de píxeles en Kotlin daría el mismo resultado, peor y
 * mucho más despacio.
 */
object RecorteDeHoja {
    /**
     * Descomprime la foto reducida y con el giro de la cámara ya aplicado.
     *
     * Lo segundo es imprescindible aquí: la cámara del teléfono guarda el fotograma como
     * lo lee el sensor —casi siempre apaisado— y anota aparte que hay que girarlo. Si las
     * esquinas se marcan sobre la imagen sin girar, el recorte sale de otro sitio.
     */
    fun fotoDe(
        fichero: File,
        ladoMaximo: Int = LADO_MAXIMO,
    ): Bitmap {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(fichero.absolutePath, medidas)
        if (medidas.outWidth <= 0) throw IOException("La cámara no ha dejado una foto legible en $fichero")

        var reduccion = 1
        while (maxOf(medidas.outWidth, medidas.outHeight) / reduccion > ladoMaximo) {
            reduccion *= 2
        }
        val mapa =
            BitmapFactory.decodeFile(fichero.absolutePath, BitmapFactory.Options().apply { inSampleSize = reduccion })
                ?: throw IOException("No se ha podido descomprimir la foto")

        val grados = giroDe(fichero)
        if (grados == 0f) return mapa
        return try {
            Bitmap.createBitmap(mapa, 0, 0, mapa.width, mapa.height, Matrix().apply { postRotate(grados) }, true)
        } finally {
            mapa.recycle()
        }
    }

    /**
     * La hoja enderezada, en un mapa de bits nuevo.
     *
     * [esquinas] va **en fracciones de la foto**, no en píxeles: es lo que permite marcar
     * el recorte sobre una previsualización pequeña y aplicarlo sobre la foto grande sin
     * que se descoloque.
     *
     * El fondo se pinta de blanco antes de nada porque el cuadrilátero puede sobresalir
     * de la foto —un tirador arrastrado fuera del borde— y lo que quede sin cubrir tiene
     * que parecer papel, no un agujero transparente que en el PDF saldría negro.
     */
    fun corregir(
        foto: Bitmap,
        esquinas: EsquinasDeHoja,
    ): Bitmap {
        val enPixeles = esquinas.escalada(foto.width.toFloat(), foto.height.toFloat())
        val tope = maxOf(foto.width, foto.height)
        val ancho = enPixeles.anchoCorregido.coerceIn(1, tope)
        val alto = enPixeles.altoCorregido.coerceIn(1, tope)

        val enderezada = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(enderezada)
        lienzo.drawColor(Color.WHITE)
        lienzo.drawBitmap(foto, Matrix().apply { setValues(homografiaDe(enPixeles, ancho, alto)) }, PINCEL)
        return enderezada
    }

    /**
     * Recorta [origen] y deja el resultado en [destino] como JPEG.
     *
     * Es la operación completa que necesita el escáner, y está aquí y no en el modelo de
     * la pantalla por una razón concreta: entre la foto de entrada y el fichero de salida
     * hay dos mapas de bits grandes vivos a la vez, y el sitio donde se reservan es el
     * mismo donde hay que acordarse de soltarlos.
     */
    fun corregirAFichero(
        origen: File,
        esquinas: EsquinasDeHoja,
        destino: File,
    ) {
        val foto = fotoDe(origen)
        try {
            val enderezada = corregir(foto, esquinas)
            try {
                destino.parentFile?.mkdirs()
                destino.outputStream().use { salida ->
                    enderezada.compress(Bitmap.CompressFormat.JPEG, CALIDAD, salida)
                }
            } finally {
                enderezada.recycle()
            }
        } finally {
            foto.recycle()
        }
    }

    private fun giroDe(fichero: File): Float =
        runCatching {
            when (
                ExifInterface(fichero.absolutePath)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> CUARTO_DE_VUELTA
                ExifInterface.ORIENTATION_ROTATE_180 -> MEDIA_VUELTA
                ExifInterface.ORIENTATION_ROTATE_270 -> TRES_CUARTOS_DE_VUELTA
                else -> 0f
            }
        }.getOrDefault(0f)

    /** Con filtrado: sin él, enderezar una hoja deja el texto con los bordes dentados. */
    private val PINCEL = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** Lo mismo que se le pide a una imagen que entra por el selector: un A4 a 200 ppp y algo. */
    private const val LADO_MAXIMO = 2400

    /**
     * Más alta que la del conversor a propósito: esta imagen se va a volver a comprimir al
     * montar el PDF, y dos pasadas de JPEG al mismo nivel se notan en el texto.
     */
    private const val CALIDAD = 95

    private const val CUARTO_DE_VUELTA = 90f
    private const val MEDIA_VUELTA = 180f
    private const val TRES_CUARTOS_DE_VUELTA = 270f
}
