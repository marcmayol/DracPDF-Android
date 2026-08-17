package com.marcmayol.dracpdf.ui.visor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.dominio.modelo.PuntoPt
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.modelo.TamanoPt
import com.marcmayol.dracpdf.ui.tema.ColoresPapel
import com.marcmayol.dracpdf.ui.tema.LocalColoresLadon

/**
 * Lo que se pinta sobre la página al leer: las coincidencias de la búsqueda y el texto
 * seleccionado.
 *
 * Como el overlay de formularios, esto es **papel y no interfaz**: los colores salen
 * de [ColoresPapel] y no del tema, porque un resaltado amarillo lo sigue siendo de
 * noche. Y como allí, las posiciones son fracciones de página: sobreviven al pellizco
 * sin recalcular nada.
 */
@Composable
fun OverlayResaltados(
    coincidencias: List<RectPt>,
    activa: RectPt?,
    tamano: TamanoPt,
    ancho: Dp,
    alto: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().testTag(TAG_OVERLAY_RESALTADOS)) {
        coincidencias.forEach { marco ->
            val esLaActiva = marco == activa
            Box(
                modifier =
                    Modifier
                        .offset(x = ancho * (marco.x0 / tamano.ancho), y = alto * (marco.y0 / tamano.alto))
                        .size(
                            width = ancho * (marco.ancho / tamano.ancho),
                            height = alto * (marco.alto / tamano.alto),
                        ).clip(RoundedCornerShape(2.dp))
                        .background(if (esLaActiva) ColoresPapel.coincidenciaActiva else ColoresPapel.coincidencia)
                        .then(
                            // La activa lleva además borde: en una página con nueve
                            // resaltados amarillos, el color solo no basta para saber
                            // en cuál se está.
                            if (esLaActiva) {
                                Modifier.border(
                                    GROSOR_ACTIVA,
                                    ColoresPapel.coincidenciaActivaBorde,
                                    RoundedCornerShape(2.dp),
                                )
                            } else {
                                Modifier
                            },
                        ),
            )
        }
    }
}

/**
 * El texto seleccionado, con sus dos asas.
 *
 * Las asas se arrastran en píxeles de pantalla y el documento habla en puntos de
 * página, así que la conversión se hace aquí, que es el único sitio que conoce las dos
 * cosas: cuánto mide la página en la pantalla y cuánto mide de verdad.
 */
@Composable
fun OverlaySeleccion(
    seleccion: SeleccionEnCurso,
    tamano: TamanoPt,
    ancho: Dp,
    alto: Dp,
    alMoverAsa: (Boolean, PuntoPt) -> Unit,
    modifier: Modifier = Modifier,
) {
    val densidad = LocalDensity.current
    val puntosPorPixelX = with(densidad) { tamano.ancho / ancho.toPx() }
    val puntosPorPixelY = with(densidad) { tamano.alto / alto.toPx() }
    val marcos = seleccion.seleccion.marcos

    Box(modifier = modifier.fillMaxSize().testTag(TAG_OVERLAY_SELECCION)) {
        marcos.forEach { marco ->
            Box(
                modifier =
                    Modifier
                        .offset(x = ancho * (marco.x0 / tamano.ancho), y = alto * (marco.y0 / tamano.alto))
                        .size(
                            width = ancho * (marco.ancho / tamano.ancho),
                            height = alto * (marco.alto / tamano.alto),
                        ).background(ColoresPapel.seleccion),
            )
        }

        // Las asas van en las esquinas de fuera de la selección —abajo a la izquierda
        // de la primera línea y abajo a la derecha de la última—, que es donde las pone
        // cualquier sistema y donde el dedo no tapa lo que se está ajustando.
        marcos.firstOrNull()?.let { primero ->
            Asa(
                x = ancho * (primero.x0 / tamano.ancho),
                y = alto * (primero.y1 / tamano.alto),
                tag = TAG_ASA_INICIAL,
                alArrastrar = { dx, dy ->
                    alMoverAsa(
                        true,
                        PuntoPt(primero.x0 + dx * puntosPorPixelX, primero.y0 + dy * puntosPorPixelY),
                    )
                },
            )
        }
        marcos.lastOrNull()?.let { ultimo ->
            Asa(
                x = ancho * (ultimo.x1 / tamano.ancho),
                y = alto * (ultimo.y1 / tamano.alto),
                tag = TAG_ASA_FINAL,
                alArrastrar = { dx, dy ->
                    alMoverAsa(
                        false,
                        PuntoPt(ultimo.x1 + dx * puntosPorPixelX, ultimo.y1 + dy * puntosPorPixelY),
                    )
                },
            )
        }
    }
}

/**
 * Un asa de selección: el círculo que se arrastra.
 *
 * Es más grande que el punto que marca porque se coge con el dedo; el círculo visible
 * es pequeño y el área que responde, la de siempre.
 */
@Composable
private fun Asa(
    x: Dp,
    y: Dp,
    tag: String,
    alArrastrar: (Float, Float) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .offset(x = x - LADO_ASA / 2, y = y)
                .size(LADO_ASA)
                .clip(CircleShape)
                // El asa sí es interfaz —la pone la aplicación, no el documento— pero
                // su color vale igual en los dos temas: va sobre papel.
                .background(LocalColoresLadon.current.handleSeleccion)
                .pointerInput(tag) {
                    detectDragGestures { cambio, arrastre ->
                        cambio.consume()
                        alArrastrar(arrastre.x, arrastre.y)
                    }
                }.testTag(tag),
    )
}

private val LADO_ASA = 20.dp
private val GROSOR_ACTIVA = 1.5.dp

const val TAG_OVERLAY_RESALTADOS = "overlay_resaltados"
const val TAG_OVERLAY_SELECCION = "overlay_seleccion"
const val TAG_ASA_INICIAL = "seleccion_asa_inicial"
const val TAG_ASA_FINAL = "seleccion_asa_final"
