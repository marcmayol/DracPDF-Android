package com.marcmayol.dracpdf.ui.visor

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.marcmayol.dracpdf.dominio.modelo.PaginaRenderizada
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/** Una página a una escala concreta. Es la clave de la caché. */
data class ClavePagina(
    val pagina: Int,
    val escalaCuantizada: Int,
)

/**
 * Convierte lo que devuelve el dominio en algo que Compose sepa dibujar.
 *
 * MuPDF entrega cuatro bytes RGBA por píxel y un `Bitmap` ARGB_8888 de Android
 * guarda exactamente eso en el mismo orden, así que la copia es directa y no hay que
 * reordenar canales.
 */
fun PaginaRenderizada.aImageBitmap(): ImageBitmap {
    val mapa = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
    mapa.copyPixelsFromBuffer(ByteBuffer.wrap(pixeles))
    return mapa.asImageBitmap()
}

/**
 * Caché de páginas rasterizadas, **medida en bytes de verdad** y no en número de
 * entradas: una página A4 a ancho de pantalla ocupa unos 6 MB y a tres aumentos
 * cincuenta y cuatro. Contar entradas sería contar cualquier cosa menos memoria.
 *
 * El presupuesto sale de lo que el sistema concede a este proceso. Pasarse no da un
 * aviso: da un cierre por falta de memoria.
 */
class CachePaginas(
    presupuestoBytes: Int,
) {
    private val cache =
        object : LruCache<ClavePagina, ImageBitmap>(presupuestoBytes) {
            override fun sizeOf(
                clave: ClavePagina,
                valor: ImageBitmap,
            ): Int = valor.asAndroidBitmap().allocationByteCount
        }

    val presupuesto: Int = presupuestoBytes

    val ocupado: Int get() = cache.size()

    operator fun get(clave: ClavePagina): ImageBitmap? = cache.get(clave)

    fun guardar(
        clave: ClavePagina,
        pagina: ImageBitmap,
    ) {
        cache.put(clave, pagina)
    }

    /** Vacía la caché. Lo llama `onTrimMemory` antes de que el sistema se ponga serio. */
    fun vaciar() {
        cache.evictAll()
    }

    /**
     * Olvida una página, a todas sus escalas.
     *
     * Hace falta al rellenar un campo: lo que hay en la caché es la página de antes de
     * escribir, y si no se tira, el usuario ve su texto en el overlay pero el papel de
     * debajo sigue vacío. La página se vuelve a pedir sola en cuanto está a la vista.
     */
    fun olvidar(pagina: Int) {
        cache
            .snapshot()
            .keys
            .filter { it.pagina == pagina }
            .forEach(cache::remove)
    }

    companion object {
        private const val MEGA = 1024 * 1024
        private const val FRACCION_DE_MEMORIA = 4
        private const val MINIMO_MB = 32
        private const val MAXIMO_MB = 96

        /**
         * Un cuarto de lo que el sistema concede al proceso, entre 32 y 96 MB.
         *
         * El techo no es tacañería: el resto de la memoria hace falta para el
         * documento nativo, los bitmaps en vuelo y la propia interfaz, y una caché que
         * se come todo el presupuesto no acelera nada, sólo adelanta el cierre.
         */
        fun presupuestoPara(contexto: Context): Int {
            val gestor = contexto.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mb = (gestor.memoryClass / FRACCION_DE_MEMORIA).coerceIn(MINIMO_MB, MAXIMO_MB)
            return mb * MEGA
        }

        /**
         * Cuantiza la escala a pasos de 0,25 antes de tocar la caché.
         *
         * Sin esto, un pellizco pide un render distinto por fotograma —2,03 · 2,07 ·
         * 2,11…—, ninguno se reutiliza jamás y el presupuesto se evapora en un gesto.
         */
        fun cuantizar(escala: Float): Int = (escala * PASOS_POR_UNIDAD).roundToInt()

        fun escalaDe(cuantizada: Int): Float = cuantizada / PASOS_POR_UNIDAD.toFloat()

        private const val PASOS_POR_UNIDAD = 4
    }
}
