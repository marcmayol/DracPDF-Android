package com.marcmayol.dracpdf.ui.firmas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import java.io.ByteArrayOutputStream
import androidx.compose.foundation.Canvas as LienzoCompose

/**
 * Lo que se lleva dibujado en el lienzo.
 *
 * Los trazos se guardan como listas de puntos y no como un mapa de píxeles: mientras
 * se dibuja, un trazo se puede volver a pintar tantas veces como haga falta —y con
 * el suavizado aplicado— sin arrastrar una imagen a cada fotograma. Los píxeles sólo
 * aparecen al final, al exportar.
 */
class EstadoLienzo {
    private val _trazos = mutableStateListOf<List<Offset>>()
    val trazos: List<List<Offset>> get() = _trazos

    var enCurso by mutableStateOf<List<Offset>>(emptyList())
        private set

    val hayTinta: Boolean get() = _trazos.isNotEmpty() || enCurso.size > 1

    fun empezar(punto: Offset) {
        enCurso = listOf(punto)
    }

    fun continuar(punto: Offset) {
        enCurso = enCurso + punto
    }

    fun terminar() {
        if (enCurso.size > 1) _trazos.add(enCurso)
        enCurso = emptyList()
    }

    fun limpiar() {
        _trazos.clear()
        enCurso = emptyList()
    }
}

/**
 * El lienzo donde se firma con el dedo.
 *
 * Aquí el móvil gana al escritorio: firmar con el dedo o con un lápiz se parece a
 * firmar, y con el ratón no se parece a nada. Por eso esta pantalla existe tal cual
 * y no como un «adjuntar imagen».
 *
 * El fondo es transparente **desde el principio**, no se pinta de blanco para
 * borrarlo luego: lo que se dibuja aquí es lo mismo que acabará dentro del PDF, y un
 * blanco que se cuele taparía el documento al estamparla encima.
 */
@Composable
fun LienzoFirma(
    estado: EstadoLienzo,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF111318),
    grosor: Float = GROSOR_TRAZO,
) {
    LienzoCompose(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .testTag(TAG_LIENZO)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { punto -> estado.empezar(punto) },
                        onDrag = { cambio, _ ->
                            cambio.consume()
                            estado.continuar(cambio.position)
                        },
                        onDragEnd = { estado.terminar() },
                        onDragCancel = { estado.terminar() },
                    )
                },
    ) {
        estado.trazos.forEach { trazo -> dibujarTrazo(trazo, color, grosor) }
        if (estado.enCurso.size > 1) dibujarTrazo(estado.enCurso, color, grosor)
    }
}

private fun DrawScope.dibujarTrazo(
    puntos: List<Offset>,
    color: Color,
    grosor: Float,
) {
    drawPath(
        path = caminoDe(puntos),
        color = color,
        style = Stroke(width = grosor, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/**
 * Convierte los puntos que ha ido soltando el dedo en una curva.
 *
 * **Unir los puntos con rectas no vale.** El dedo entrega posiciones cada pocos
 * milisegundos y una firma hecha de segmentos rectos se ve exactamente como lo que
 * es: un garabato poligonal. Cada par de puntos se une con una curva cuadrática cuyo
 * control es el punto anterior y cuyo final es el punto medio del siguiente tramo,
 * que es lo que hace que los cambios de dirección salgan redondeados en vez de en
 * pico.
 */
fun caminoDe(puntos: List<Offset>): Path =
    Path().apply {
        if (puntos.isEmpty()) return@apply
        moveTo(puntos.first().x, puntos.first().y)
        if (puntos.size == 1) {
            // Un toque sin arrastre: un punto de tinta y no una línea de longitud cero,
            // que no dibujaría nada.
            lineTo(puntos.first().x + PUNTO_SUELTO, puntos.first().y)
            return@apply
        }
        for (indice in 1 until puntos.size) {
            val anterior = puntos[indice - 1]
            val actual = puntos[indice]
            val medio = Offset((anterior.x + actual.x) / 2f, (anterior.y + actual.y) / 2f)
            quadraticTo(anterior.x, anterior.y, medio.x, medio.y)
        }
        lineTo(puntos.last().x, puntos.last().y)
    }

/** Una firma ya exportada: los bytes del PNG y lo que mide. */
data class FirmaDibujada(
    val png: ByteArray,
    val anchoPx: Int,
    val altoPx: Int,
) {
    // Generados a mano porque el PNG es un ByteArray y equals/hashCode de data class
    // compararían la referencia del array, que no dice nada.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is FirmaDibujada &&
                    anchoPx == other.anchoPx &&
                    altoPx == other.altoPx &&
                    png.contentEquals(other.png)
            )

    override fun hashCode(): Int = png.contentHashCode() * 31 + anchoPx * 31 + altoPx
}

/**
 * Rasteriza los trazos a un PNG **recortado a la tinta** y con fondo transparente.
 *
 * El recorte importa tanto como el dibujo: quien firma usa un trozo del lienzo, y
 * exportar el lienzo entero metería un montón de aire alrededor de la firma. Al
 * colocarla sobre la página, ese aire haría que la firma se viera diminuta dentro de
 * un rectángulo enorme y que su proporción no fuera la suya.
 *
 * @return `null` si no hay nada dibujado.
 */
fun exportarFirma(
    trazos: List<List<Offset>>,
    color: Int = COLOR_TINTA,
    grosor: Float = GROSOR_TRAZO,
): FirmaDibujada? {
    val puntos = trazos.flatten()
    if (puntos.isEmpty()) return null

    // El margen es medio grosor: el trazo se dibuja centrado en el punto, así que sin
    // esto se recortaría justo por la mitad de la línea de los bordes.
    val margen = grosor
    val minX = puntos.minOf { it.x } - margen
    val minY = puntos.minOf { it.y } - margen
    val ancho = (puntos.maxOf { it.x } + margen - minX).toInt().coerceAtLeast(1)
    val alto = (puntos.maxOf { it.y } + margen - minY).toInt().coerceAtLeast(1)

    val mapa = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
    val lienzo = Canvas(mapa)
    val pincel =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = grosor
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    trazos.forEach { trazo ->
        val trasladado = trazo.map { Offset(it.x - minX, it.y - minY) }
        lienzo.drawPath(caminoDe(trasladado).asAndroidPath(), pincel)
    }

    val bytes =
        ByteArrayOutputStream().use { salida ->
            mapa.compress(Bitmap.CompressFormat.PNG, CALIDAD_PNG, salida)
            salida.toByteArray()
        }
    mapa.recycle()
    return FirmaDibujada(bytes, ancho, alto)
}

/** Tinta del diseño: la firma nunca se recolorea. */
private const val COLOR_TINTA = 0xFF111318.toInt()

const val GROSOR_TRAZO = 6f

/** Lo que se alarga un toque sin arrastre para que llegue a dibujarse. */
private const val PUNTO_SUELTO = 0.1f

private const val CALIDAD_PNG = 100

const val TAG_LIENZO = "firma_lienzo"
