package com.marcmayol.dracpdf.ui.visor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.dominio.modelo.TamanoPt
import com.marcmayol.dracpdf.ui.tema.ColoresPapel

/**
 * La firma flotando sobre la página mientras se coloca.
 *
 * **Todavía no está en el documento.** Esto es una vista previa que se arrastra y se
 * estira; el PDF no se toca hasta que se confirma, y por eso cancelar no tiene que
 * deshacer nada.
 *
 * El marco y el asa se dibujan con los colores de papel del modo de colocación, los
 * mismos que usa el campo activo del formulario: es el documento quien los lleva, no
 * la interfaz, y no cambian con el tema del teléfono.
 */
@Composable
fun OverlayColocacion(
    firma: ImageBitmap,
    colocacion: ColocacionFirma,
    tamano: TamanoPt,
    ancho: Dp,
    alto: Dp,
    alArrastrar: (dxPt: Float, dyPt: Float) -> Unit,
    alRedimensionar: (dAnchoPt: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val marco = colocacion.marco
    // Lo que mide un punto de página en pantalla, que es lo que convierte el arrastre
    // del dedo en el desplazamiento del marco dentro del documento.
    val puntoEnDp = ancho / tamano.ancho

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .offset(x = puntoEnDp * marco.x0, y = puntoEnDp * marco.y0)
                    .size(width = puntoEnDp * marco.ancho, height = puntoEnDp * marco.alto)
                    .border(GROSOR_MARCO, ColoresPapel.campoActivoBorde)
                    .testTag(TAG_COLOCACION)
                    .pointerInput(colocacion.pagina) {
                        detectDragGestures { cambio, arrastre ->
                            cambio.consume()
                            // De píxeles de pantalla a puntos de página: el modelo no
                            // sabe a qué zoom se está mirando, y no tiene por qué.
                            alArrastrar(arrastre.x / puntoEnDp.toPx(), arrastre.y / puntoEnDp.toPx())
                        }
                    },
        ) {
            Image(
                bitmap = firma,
                contentDescription = "Firma que se está colocando",
                modifier = Modifier.fillMaxSize(),
                // La firma se estira al marco entero porque el marco conserva su
                // proporción: el modelo no deja cambiar una sin la otra.
                contentScale = ContentScale.FillBounds,
            )
        }

        // El asa, en la esquina de abajo a la derecha y **fuera** del marco: dentro,
        // el dedo taparía justo la esquina que hay que ver para ajustarla.
        Box(
            modifier =
                Modifier
                    .offset(
                        x = puntoEnDp * marco.x1 - TAMANO_ASA / 2,
                        y = puntoEnDp * marco.y1 - TAMANO_ASA / 2,
                    ).size(TAMANO_ASA)
                    .clip(CircleShape)
                    .background(ColoresPapel.campoActivoBorde)
                    .testTag(TAG_ASA)
                    .pointerInput(colocacion.pagina) {
                        detectDragGestures { cambio, arrastre ->
                            cambio.consume()
                            alRedimensionar(arrastre.x / puntoEnDp.toPx())
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {}
    }
}

private val GROSOR_MARCO = 1.5.dp

/** 44 dp: el asa hay que poder cogerla con el dedo, no con un cursor. */
private val TAMANO_ASA = 44.dp

const val TAG_COLOCACION = "visor_colocacion_firma"
const val TAG_ASA = "visor_colocacion_asa"
