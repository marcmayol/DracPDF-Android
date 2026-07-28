package com.marcmayol.dracpdf.ui.visor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.marcmayol.dracpdf.ui.tema.MedidasLadon
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * El visor.
 *
 * Regla que gobierna la pantalla y que viene del diseño: **el documento es la
 * pantalla**; el chrome es un invitado que se va solo. Un toque en la página oculta
 * las dos barras y deja sólo la píldora; el scroll no las oculta, porque competiría
 * con el pellizco.
 */
@Composable
fun PantallaVisor(
    modelo: VisorViewModel,
    alSalir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by modelo.estado.collectAsState()
    val paginas by modelo.paginas.collectAsState()
    val lista = rememberLazyListState()

    // El zoom del gesto va aparte del zoom fijado: durante el pellizco sólo se estira
    // el bitmap que ya hay —gratis, lo hace la GPU— y el render nítido se pide cuando
    // el dedo se detiene. Rasterizar por fotograma daría un tirón en cada uno.
    var zoomVivo by remember { mutableFloatStateOf(estado.zoom) }
    LaunchedEffect(estado.zoom) { zoomVivo = estado.zoom }
    LaunchedEffect(zoomVivo) {
        // Cada cambio cancela esta espera; cuando el gesto para, se fija el zoom y
        // llega el render a la escala nueva.
        delay(ESPERA_TRAS_EL_GESTO_MS)
        modelo.fijarZoom(zoomVivo)
    }

    LaunchedEffect(lista) {
        snapshotFlow {
            val visibles = lista.layoutInfo.visibleItemsInfo
            if (visibles.isEmpty()) null else visibles.first().index to visibles.last().index
        }.collect { ventana ->
            ventana?.let { (primera, ultima) -> modelo.alCambiarVentana(primera, ultima) }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        AnimatedVisibility(visible = estado.chromeVisible, enter = fadeIn(), exit = fadeOut()) {
            BarraSuperiorVisor(nombre = estado.nombre, alSalir = alSalir)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ListaDePaginas(
                estado = estado,
                paginas = paginas,
                lista = lista,
                zoomVivo = zoomVivo,
                tamanoDe = modelo::tamanoDe,
                alTocar = modelo::alternarChrome,
                alDobleTocar = {
                    modelo.fijarZoom(if (estado.zoom > AJUSTE_ANCHO) AJUSTE_ANCHO else CIEN_POR_CIEN)
                },
                alPellizcar = { factor ->
                    zoomVivo = (zoomVivo * factor).coerceIn(VisorViewModel.ZOOM_MINIMO, VisorViewModel.ZOOM_MAXIMO)
                },
            )

            if (estado.paginas > 0) {
                PildoraPagina(
                    texto = "${estado.paginaActual + 1} / ${estado.paginas}",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
            }
        }

        AnimatedVisibility(visible = estado.chromeVisible, enter = fadeIn(), exit = fadeOut()) {
            BarraInferiorVisor()
        }
    }
}

@Composable
private fun ListaDePaginas(
    estado: EstadoVisor,
    paginas: Map<ClavePagina, ImageBitmap>,
    lista: LazyListState,
    zoomVivo: Float,
    tamanoDe: (Int) -> TamanoPt?,
    alTocar: () -> Unit,
    alDobleTocar: () -> Unit,
    alPellizcar: (Float) -> Unit,
) {
    val scrollHorizontal = rememberScrollState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val anchoPagina: Dp = maxWidth * zoomVivo

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Con zoom la página es más ancha que la pantalla, así que la
                    // columna entera se desplaza en horizontal.
                    .then(if (zoomVivo > 1f) Modifier.horizontalScroll(scrollHorizontal) else Modifier)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { alTocar() }, onDoubleTap = { alDobleTocar() })
                    }.pointerInput(Unit) {
                        detectTransformGestures { _, _, factor, _ ->
                            if (factor != 1f) alPellizcar(factor)
                        }
                    },
        ) {
            LazyColumn(
                state = lista,
                modifier = Modifier.width(anchoPagina).fillMaxSize().testTag(TAG_LISTA),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MedidasLadon.hueco),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                items(count = estado.paginas, key = { indice -> indice }) { indice ->
                    val tamano = tamanoDe(indice)
                    val proporcion = if (tamano == null) PROPORCION_A4 else tamano.alto / tamano.ancho
                    PaginaDelDocumento(
                        indice = indice,
                        mapa = mejorDisponible(paginas, indice, zoomVivo),
                        ancho = anchoPagina,
                        alto = anchoPagina * proporcion,
                    )
                }
            }
        }
    }
}

/**
 * El bitmap con el que dibujar una página: el de la escala pedida si ya está y, si
 * no, el de la escala más cercana que haya.
 *
 * Es lo que evita que la página parpadee al hacer zoom: se sigue viendo la versión
 * anterior, estirada, hasta que llega la nítida.
 */
private fun mejorDisponible(
    paginas: Map<ClavePagina, ImageBitmap>,
    indice: Int,
    zoom: Float,
): ImageBitmap? {
    val exacta = ClavePagina(indice, CachePaginas.cuantizar(zoom))
    paginas[exacta]?.let { return it }
    return paginas
        .filterKeys { it.pagina == indice }
        .minByOrNull { abs(it.key.escalaCuantizada - exacta.escalaCuantizada) }
        ?.value
}

@Composable
private fun PaginaDelDocumento(
    indice: Int,
    mapa: ImageBitmap?,
    ancho: Dp,
    alto: Dp,
) {
    Box(
        modifier =
            Modifier
                .width(ancho)
                .height(alto)
                // El papel es papel en los dos temas: el documento nunca se oscurece.
                .background(ColoresPapel.papel, RoundedCornerShape(2.dp))
                .testTag(tagPagina(indice)),
    ) {
        if (mapa != null) {
            Image(
                bitmap = mapa,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

@Composable
private fun PildoraPagina(
    texto: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(MedidasLadon.pildoraPagina)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = OPACIDAD_PILDORA))
                .padding(horizontal = 14.dp)
                .testTag(TAG_PILDORA),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

private const val PROPORCION_A4 = 842f / 595f
private const val OPACIDAD_PILDORA = 0.92f
private const val AJUSTE_ANCHO = 1f

/** El «100 %» del doble toque: el doble del ajuste a ancho, que en A4 deja el texto legible. */
private const val CIEN_POR_CIEN = 2f

private const val ESPERA_TRAS_EL_GESTO_MS = 180L

const val TAG_LISTA = "visor_lista"
const val TAG_PILDORA = "visor_pildora_pagina"

fun tagPagina(indice: Int): String = "visor_pagina_$indice"
