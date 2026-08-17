package com.marcmayol.dracpdf.adaptadores.conversion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.Rect
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/**
 * Una imagen del teléfono, puesta en una página.
 *
 * **Una imagen es una página.** No se juntan dos en una hoja ni se parte una grande en
 * varias: quien elige seis fotos de un contrato espera seis páginas, y cualquier otra
 * cosa le obligaría a contarlas para saber qué ha salido.
 *
 * **La página se orienta como la imagen.** Una foto apaisada metida a la fuerza en un A4
 * vertical queda del tamaño de un sello con dos palmos de blanco arriba y abajo; girar la
 * hoja en vez de encoger la foto es lo que hace cualquier programa de escritorio, y lo
 * que el usuario espera al ver el resultado. Lo que **nunca** se hace es estirarla para
 * que llene: la proporción se respeta siempre y el blanco que sobra se reparte a los dos
 * lados.
 */
internal class ImagenesAPdf {
    /**
     * Añade [bytes] como una página nueva al final de [pdf].
     *
     * @return cuántas páginas se han añadido, que siempre es una. Se devuelve igualmente
     *   para que quien monta el documento sume sin tener que saber qué hace cada tipo de
     *   entrada.
     */
    fun anadirPagina(
        pdf: PDFDocument,
        bytes: ByteArray,
    ): Int {
        val normalizada = normalizar(bytes)
        val imagen = Image(normalizada.bytes)
        val referencia =
            try {
                pdf.addImage(imagen)
            } finally {
                imagen.destroy()
            }

        val caja = cajaPara(normalizada.ancho, normalizada.alto)
        val recursos =
            pdf.newDictionary().apply {
                put("XObject", pdf.newDictionary().apply { put(NOMBRE_IMAGEN, referencia) })
            }
        val hoja = pdf.addPage(caja, 0, recursos, dibujoDe(normalizada, caja))
        pdf.insertPage(-1, hoja)
        return 1
    }

    /** La hoja A4 en la orientación de la imagen. */
    private fun cajaPara(
        ancho: Int,
        alto: Int,
    ): Rect =
        if (ancho > alto) {
            Rect(0f, 0f, HojaA4.ALTO, HojaA4.ANCHO)
        } else {
            Rect(0f, 0f, HojaA4.ANCHO, HojaA4.ALTO)
        }

    /**
     * El lenguaje de dibujo del PDF para poner la imagen centrada y a escala.
     *
     * La matriz `cm` de un XObject de imagen lleva **el tamaño en puntos**, no un factor:
     * la imagen se dibuja siempre en el cuadrado unidad y es esa matriz la que dice
     * cuánto mide y dónde empieza. Los dos números de la diagonal son el ancho y el alto
     * finales, y por eso basta con calcular uno solo —la escala que quepa— y multiplicar:
     * al ser el mismo para los dos lados, la proporción no se puede perder.
     */
    private fun dibujoDe(
        imagen: ImagenNormalizada,
        caja: Rect,
    ): String {
        val disponibleAncho = caja.x1 - caja.x0 - 2 * MARGEN
        val disponibleAlto = caja.y1 - caja.y0 - 2 * MARGEN
        val escala = min(disponibleAncho / imagen.ancho, disponibleAlto / imagen.alto)
        val ancho = imagen.ancho * escala
        val alto = imagen.alto * escala
        val x = (caja.x1 - caja.x0 - ancho) / 2
        val y = (caja.y1 - caja.y0 - alto) / 2
        return "q ${ancho.enPdf()} 0 0 ${alto.enPdf()} ${x.enPdf()} ${y.enPdf()} cm /$NOMBRE_IMAGEN Do Q\n"
    }

    /**
     * La imagen tal como va a entrar en el PDF: descomprimida, girada y vuelta a
     * comprimir.
     *
     * Podría parecer que lo limpio es meter los bytes del fichero tal cual —MuPDF sabe
     * leer JPEG y PNG— y así no se recomprimiría nada. Se hace al revés por tres motivos
     * que no tienen vuelta:
     *
     * 1. **WEBP.** El usuario tiene el teléfono lleno de WEBP porque es lo que bajan el
     *    navegador y la mensajería, y ese formato lo decodifica Android, no el motor.
     * 2. **La orientación EXIF.** Una foto hecha en vertical viaja apaisada con una nota
     *    que dice «gírame». Quien ignora la nota mete la hoja tumbada, y es el fallo más
     *    visible que puede tener esto.
     * 3. **El tamaño.** Doce megapíxeles en una hoja A4 son cuatro veces más píxeles de
     *    los que se ven al imprimirla, y treinta de esos hacen un PDF que no se abre en
     *    ningún sitio.
     */
    private fun normalizar(bytes: ByteArray): ImagenNormalizada {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
        if (medidas.outWidth <= 0 || medidas.outHeight <= 0) {
            throw IOException("Esto no es una imagen que Android sepa leer")
        }

        val opciones = BitmapFactory.Options().apply { inSampleSize = reduccionPara(medidas) }
        val mapa =
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones)
                ?: throw IOException("No se ha podido descomprimir la imagen")

        return try {
            val derecha = enderezar(mapa, giroExifDe(bytes))
            try {
                ImagenNormalizada(comprimir(derecha), derecha.width, derecha.height)
            } finally {
                if (derecha !== mapa) derecha.recycle()
            }
        } finally {
            mapa.recycle()
        }
    }

    /**
     * A la mitad, a la cuarta parte… hasta que el lado largo baje del tope.
     *
     * `inSampleSize` sólo entiende potencias de dos y descarta píxeles al vuelo, sin
     * llegar a reservar el mapa entero: es la única manera de abrir una foto de móvil sin
     * pedirle a Android sesenta megas de memoria para tirar la mitad después.
     */
    private fun reduccionPara(medidas: BitmapFactory.Options): Int {
        var reduccion = 1
        while (max(medidas.outWidth, medidas.outHeight) / reduccion > LADO_MAXIMO) {
            reduccion *= 2
        }
        return reduccion
    }

    /** El giro que la cámara anotó en la foto, en grados horarios. */
    private fun giroExifDe(bytes: ByteArray): Int =
        runCatching {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> CUARTO_DE_VUELTA
                ExifInterface.ORIENTATION_ROTATE_180 -> MEDIA_VUELTA
                ExifInterface.ORIENTATION_ROTATE_270 -> TRES_CUARTOS_DE_VUELTA
                else -> 0
            }
            // Un PNG o un WEBP no llevan EXIF y ExifInterface protesta; no es un fallo de
            // la imagen, es que no había nada que leer.
        }.getOrDefault(0)

    private fun enderezar(
        mapa: Bitmap,
        grados: Int,
    ): Bitmap {
        if (grados == 0) return mapa
        val giro = Matrix().apply { postRotate(grados.toFloat()) }
        return Bitmap.createBitmap(mapa, 0, 0, mapa.width, mapa.height, giro, true)
    }

    /**
     * PNG si la imagen tiene transparencia y JPEG si no.
     *
     * No es una preferencia estética: un JPEG no sabe guardar el canal alfa, así que una
     * captura de pantalla con fondo transparente saldría con los huecos en negro. Y al
     * revés, guardar una fotografía en PNG multiplica su peso por cinco sin ganar nada.
     */
    private fun comprimir(mapa: Bitmap): ByteArray {
        val formato = if (mapa.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        return ByteArrayOutputStream().use { salida ->
            mapa.compress(formato, CALIDAD, salida)
            salida.toByteArray()
        }
    }

    private data class ImagenNormalizada(
        val bytes: ByteArray,
        val ancho: Int,
        val alto: Int,
    ) {
        // Los data class con ByteArray comparan la referencia y no el contenido, y detekt
        // avisa con razón. Aquí no se compara ninguna: se lleva el tamaño junto a los
        // bytes porque medirlo otra vez obligaría a descomprimir de nuevo.
        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)
    }

    private companion object {
        const val NOMBRE_IMAGEN = "Im1"

        /**
         * Un dedo de margen. Con la imagen a sangre, cualquier impresora doméstica —que
         * no imprime hasta el borde— recorta el borde de la hoja escaneada.
         */
        const val MARGEN = 18f

        /**
         * El lado largo, en píxeles. Un A4 a 200 puntos por pulgada mide 1654 × 2339: por
         * encima de esto se guardan píxeles que ni la pantalla ni la impresora enseñan.
         */
        const val LADO_MAXIMO = 2400

        const val CALIDAD = 90

        const val CUARTO_DE_VUELTA = 90
        const val MEDIA_VUELTA = 180
        const val TRES_CUARTOS_DE_VUELTA = 270
    }
}
