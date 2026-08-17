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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.dominio.modelo.EntradaIndice
import com.marcmayol.dracpdf.ui.tema.ColoresPapel
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * La hoja de Índice, con las dos pestañas del diseño: Índice y Miniaturas.
 *
 * La pestaña de Índice enseña el *outline* del documento —su tabla de contenidos— y
 * se abre por defecto cuando lo hay: quien tiene un documento con capítulos busca por
 * capítulo, no por miniatura. Un documento sin outline abre directamente en
 * Miniaturas y deja la otra pestaña apagada, que es más honesto que enseñar una lista
 * vacía.
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
    indice: List<EntradaIndice> = emptyList(),
) {
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val hayIndice = indice.any { it.pagina != null }
    var enIndice by remember(hayIndice) { mutableStateOf(hayIndice) }

    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = estadoHoja,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.testTag(TAG_HOJA_INDICE),
    ) {
        // La hoja mide lo que mide su contenido, y su contenido es una lista que
        // querría toda la pantalla. Sin acotar la altura aquí, la lista empuja las
        // pestañas por encima del borde de arriba y desaparecen de la vista.
        Column(modifier = Modifier.fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(MedidasLadon.hueco),
            ) {
                PestanaHoja(
                    titulo = "Índice",
                    activa = enIndice,
                    habilitada = hayIndice,
                    tag = TAG_TAB_INDICE,
                    alPulsar = { enIndice = true },
                )
                PestanaHoja(
                    titulo = "Miniaturas",
                    activa = !enIndice,
                    habilitada = true,
                    tag = TAG_TAB_MINIATURAS,
                    alPulsar = { enIndice = false },
                )
            }

            IrALaPagina(paginas = paginas, alIr = alElegirPagina)

            Box(modifier = Modifier.weight(1f)) {
                if (enIndice) {
                    ListaDelIndice(indice = indice, paginaActual = paginaActual, alElegirPagina = alElegirPagina)
                } else {
                    RejillaDeMiniaturas(
                        paginas = paginas,
                        paginaActual = paginaActual,
                        miniaturas = miniaturas,
                        alPedirMiniatura = alPedirMiniatura,
                        alElegirPagina = alElegirPagina,
                    )
                }
            }
        }
    }
}

/**
 * El campo de «ir a la página».
 *
 * Está en la hoja y no en un diálogo aparte porque es la misma pregunta que se
 * contesta mirando miniaturas: a dónde quiero ir. Se valida mientras se escribe, así
 * que pedir la página 300 de un documento de 12 no lleva a ninguna parte en vez de
 * llevar al final.
 */
@Composable
private fun IrALaPagina(
    paginas: Int,
    alIr: (Int) -> Unit,
) {
    var texto by remember { mutableStateOf("") }
    val pedida = texto.toIntOrNull()?.takeIf { it in 1..paginas }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = texto,
            onValueChange = { escrito -> texto = escrito.filter(Char::isDigit).take(DIGITOS_MAXIMOS) },
            singleLine = true,
            label = { Text("Ir a la página") },
            placeholder = { Text("1 - $paginas") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { pedida?.let { alIr(it - 1) } }),
            modifier = Modifier.weight(1f).testTag(TAG_CAMPO_IR_A_PAGINA),
        )
        TextButton(
            onClick = { pedida?.let { alIr(it - 1) } },
            enabled = pedida != null,
            modifier = Modifier.testTag(TAG_IR_A_PAGINA),
        ) { Text("Ir") }
    }
}

@Composable
private fun ListaDelIndice(
    indice: List<EntradaIndice>,
    paginaActual: Int,
    alElegirPagina: (Int) -> Unit,
) {
    val barraDeNavegacion = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(TAG_LISTA_INDICE),
        contentPadding = PaddingValues(bottom = MedidasLadon.margen + barraDeNavegacion),
    ) {
        itemsIndexed(indice) { posicion, entrada ->
            EntradaDelIndice(
                entrada = entrada,
                posicion = posicion,
                esLaActual = entrada.pagina == paginaActual,
                alPulsar = { entrada.pagina?.let(alElegirPagina) },
            )
        }
    }
}

@Composable
private fun EntradaDelIndice(
    entrada: EntradaIndice,
    posicion: Int,
    esLaActual: Boolean,
    alPulsar: () -> Unit,
) {
    val navegable = entrada.pagina != null
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = MedidasLadon.areaTactil)
                .clickable(enabled = navegable, onClick = alPulsar)
                // La sangría es la jerarquía: un subcapítulo se reconoce por dónde
                // empieza, sin necesidad de dibujar el árbol.
                .padding(start = 20.dp + SANGRIA_POR_NIVEL * entrada.nivel, end = 20.dp, top = 10.dp, bottom = 10.dp)
                .testTag(tagEntradaIndice(posicion)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = entrada.titulo,
            style = MaterialTheme.typography.bodyLarge,
            color =
                when {
                    esLaActual -> MaterialTheme.colorScheme.primary
                    navegable -> MaterialTheme.colorScheme.onSurface
                    // Una entrada que no lleva a ninguna parte se ve como lo que es: un
                    // rótulo, no un enlace roto.
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        entrada.pagina?.let { pagina ->
            Text(
                text = "${pagina + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RejillaDeMiniaturas(
    paginas: Int,
    paginaActual: Int,
    miniaturas: Map<Int, ImageBitmap>,
    alPedirMiniatura: (Int) -> Unit,
    alElegirPagina: (Int) -> Unit,
) {
    val rejilla = rememberLazyGridState()
    val barraDeNavegacion = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(rejilla, paginas) {
        snapshotFlow { rejilla.layoutInfo.visibleItemsInfo.map { it.index } }
            .collect { visibles -> visibles.forEach(alPedirMiniatura) }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNAS),
        state = rejilla,
        modifier = Modifier.fillMaxSize().testTag(TAG_REJILLA_MINIATURAS),
        // El aire de abajo va en el `contentPadding` y no en un padding del
        // contenedor: así la última fila de miniaturas se puede subir por encima de
        // la barra de navegación, pero la rejilla sigue desplazándose hasta el
        // borde.
        contentPadding =
            PaddingValues(
                start = MedidasLadon.margen,
                end = MedidasLadon.margen,
                top = MedidasLadon.margen,
                bottom = MedidasLadon.margen + barraDeNavegacion,
            ),
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

@Composable
private fun PestanaHoja(
    titulo: String,
    activa: Boolean,
    habilitada: Boolean,
    tag: String,
    alPulsar: () -> Unit = {},
) {
    val color =
        when {
            !habilitada -> MaterialTheme.colorScheme.onSurface.copy(alpha = ALFA_DESHABILITADA)
            activa -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clickable(enabled = habilitada && !activa, onClick = alPulsar)
                .padding(horizontal = 4.dp)
                .testTag(tag),
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
private const val DIGITOS_MAXIMOS = 5
private val SANGRIA_POR_NIVEL = 16.dp

const val TAG_HOJA_INDICE = "hoja_indice"
const val TAG_TAB_INDICE = "indice_tab_indice"
const val TAG_TAB_MINIATURAS = "indice_tab_miniaturas"
const val TAG_REJILLA_MINIATURAS = "indice_rejilla_miniaturas"
const val TAG_LISTA_INDICE = "indice_lista"
const val TAG_CAMPO_IR_A_PAGINA = "indice_campo_pagina"
const val TAG_IR_A_PAGINA = "indice_ir_a_pagina"

fun tagMiniatura(indice: Int): String = "miniatura_$indice"

fun tagEntradaIndice(posicion: Int): String = "indice_entrada_$posicion"
