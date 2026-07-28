package com.marcmayol.dracpdf.ui.visor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.ui.tema.ColoresPapel
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * La hoja de Índice, con las dos pestañas del diseño: Índice y Miniaturas.
 *
 * En esta fase sólo funciona Miniaturas; el índice del documento (el *outline*) es
 * de la Fase 7 y su pestaña se ve deshabilitada, no escondida.
 *
 * Las miniaturas son perezosas de verdad: se pide la de cada celda cuando entra en
 * pantalla. En un documento de 500 páginas, abrir la hoja y rasterizar las 500 sería
 * exactamente el error que el visor evita en la pantalla principal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaIndice(
    paginas: Int,
    paginaActual: Int,
    miniaturas: Map<Int, ImageBitmap>,
    alPedirMiniatura: (Int) -> Unit,
    alElegirPagina: (Int) -> Unit,
    alCerrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val rejilla = rememberLazyGridState()

    LaunchedEffect(rejilla, paginas) {
        snapshotFlow { rejilla.layoutInfo.visibleItemsInfo.map { it.index } }
            .collect { visibles -> visibles.forEach(alPedirMiniatura) }
    }

    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = estadoHoja,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.testTag(TAG_HOJA_INDICE),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(MedidasLadon.hueco),
        ) {
            PestanaHoja(
                titulo = "Índice",
                activa = false,
                // El outline del documento llega en la Fase 7.
                habilitada = false,
                tag = TAG_TAB_INDICE,
            )
            PestanaHoja(
                titulo = "Miniaturas",
                activa = true,
                habilitada = true,
                tag = TAG_TAB_MINIATURAS,
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNAS),
            state = rejilla,
            modifier = Modifier.fillMaxSize().testTag(TAG_REJILLA_MINIATURAS),
            contentPadding = PaddingValues(MedidasLadon.margen),
            horizontalArrangement = Arrangement.spacedBy(MedidasLadon.hueco),
            verticalArrangement = Arrangement.spacedBy(MedidasLadon.hueco),
        ) {
            items(count = paginas, key = { indice -> indice }) { indice ->
                Miniatura(
                    indice = indice,
                    mapa = miniaturas[indice],
                    esActual = indice == paginaActual,
                    alPulsar = { alElegirPagina(indice) },
                )
            }
        }
    }
}

@Composable
private fun PestanaHoja(
    titulo: String,
    activa: Boolean,
    habilitada: Boolean,
    tag: String,
) {
    val color =
        when {
            !habilitada -> MaterialTheme.colorScheme.onSurface.copy(alpha = ALFA_DESHABILITADA)
            activa -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.testTag(tag),
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = if (activa) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        if (activa) {
            // El subrayado de 2 dp en acento que marca la pestaña activa.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(MedidasLadon.contornoSeleccion)
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun Miniatura(
    indice: Int,
    mapa: ImageBitmap?,
    esActual: Boolean,
    alPulsar: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(PROPORCION_MINIATURA)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ColoresPapel.papel)
                    .then(
                        if (esActual) {
                            Modifier.border(
                                MedidasLadon.contornoSeleccion,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(3.dp),
                            )
                        } else {
                            Modifier
                        },
                    ).clickable(onClick = alPulsar)
                    .testTag(tagMiniatura(indice)),
        ) {
            if (mapa != null) {
                Image(
                    bitmap = mapa,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Text(
            text = "${indice + 1}",
            style = MaterialTheme.typography.labelSmall,
            color =
                if (esActual) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

private const val COLUMNAS = 3
private const val PROPORCION_MINIATURA = 0.72f
private const val ALFA_DESHABILITADA = 0.38f

const val TAG_HOJA_INDICE = "hoja_indice"
const val TAG_TAB_INDICE = "indice_tab_indice"
const val TAG_TAB_MINIATURAS = "indice_tab_miniaturas"
const val TAG_REJILLA_MINIATURAS = "indice_rejilla_miniaturas"

fun tagMiniatura(indice: Int): String = "miniatura_$indice"
