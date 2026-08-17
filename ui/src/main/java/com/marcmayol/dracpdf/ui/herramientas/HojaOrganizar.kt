package com.marcmayol.dracpdf.ui.herramientas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.dominio.puertos.PaginaOrdenada
import com.marcmayol.dracpdf.ui.iconos.EstadoIcono
import com.marcmayol.dracpdf.ui.iconos.IconoLadon
import com.marcmayol.dracpdf.ui.iconos.IconosLadon
import com.marcmayol.dracpdf.ui.tema.ColoresPapel
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * Organizar las páginas: extraer, eliminar, rotar y reordenar.
 *
 * Las cuatro son la misma operación vista de cuatro maneras —declarar cómo queda el
 * documento— y por eso comparten pantalla en vez de ser cuatro herramientas: quien
 * quita tres páginas y gira otra no está haciendo dos cosas, está dejando el documento
 * como lo quiere y guardándolo una vez.
 *
 * **Nada de esto toca el original.** Lo que se ve es una intención; el PDF se escribe
 * al pulsar «Guardar», y en el fichero nuevo que elija el usuario.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaOrganizar(
    paginas: Int,
    miniaturas: Map<Int, ImageBitmap>,
    alPedirMiniatura: (Int) -> Unit,
    alGuardar: (List<PaginaOrdenada>) -> Unit,
    alCerrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val orden = remember(paginas) { (0 until paginas).map { PaginaOrdenada(original = it) }.toMutableStateList() }
    var seleccion by remember(paginas) { mutableStateOf(emptySet<Int>()) }
    val rejilla = rememberLazyGridState()
    val reordenacion = remember { Reordenacion { desde, hasta -> orden.add(hasta, orden.removeAt(desde)) } }

    // Al eliminar o extraer, la rejilla encoge y los huecos de más dejan de existir.
    LaunchedEffect(orden.size) { reordenacion.olvidarSobrantes(orden.size) }

    // Las miniaturas se piden igual que en el índice: sólo las que entran en pantalla.
    // Aquí importa todavía más, porque reordenar invita a recorrer el documento entero.
    LaunchedEffect(rejilla, paginas) {
        snapshotFlow { rejilla.layoutInfo.visibleItemsInfo.map { it.index } }
            .collect { visibles -> visibles.mapNotNull { orden.getOrNull(it)?.original }.forEach(alPedirMiniatura) }
    }

    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.testTag(TAG_HOJA_ORGANIZAR),
    ) {
        Column(modifier = Modifier.fillMaxHeight().navigationBarsPadding()) {
            Cabecera(
                seleccionadas = seleccion.size,
                quedan = orden.size,
                moviendo = reordenacion.posicionArrastrada?.let { orden.getOrNull(it)?.original },
                alGuardar = { alGuardar(orden.toList()) },
            )
            AccionesDeOrganizar(
                haySeleccion = seleccion.isNotEmpty(),
                // Extraer con todo seleccionado no extrae nada: dejaría el documento
                // igual, así que se apaga en vez de fingir que ha hecho algo.
                puedeExtraer = seleccion.isNotEmpty() && seleccion.size < orden.size,
                // Eliminarlo todo no es organizar, es borrar el documento.
                puedeEliminar = seleccion.isNotEmpty() && seleccion.size < orden.size,
                alRotar = {
                    seleccion.forEach { original ->
                        val donde = orden.indexOfFirst { it.original == original }
                        if (donde >= 0) orden[donde] = orden[donde].girada()
                    }
                },
                alEliminar = {
                    orden.removeAll { it.original in seleccion }
                    seleccion = emptySet()
                },
                alExtraer = {
                    orden.removeAll { it.original !in seleccion }
                    seleccion = emptySet()
                },
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNAS),
                state = rejilla,
                contentPadding = PaddingValues(MedidasLadon.margen),
                horizontalArrangement = Arrangement.spacedBy(MedidasLadon.hueco),
                verticalArrangement = Arrangement.spacedBy(MedidasLadon.hueco),
                modifier = Modifier.fillMaxSize().testTag(TAG_REJILLA_ORGANIZAR),
            ) {
                items(count = orden.size, key = { posicion -> orden[posicion].original }) { posicion ->
                    val pagina = orden[posicion]
                    PaginaEnRejilla(
                        pagina = pagina,
                        posicion = posicion,
                        mapa = miniaturas[pagina.original],
                        seleccionada = pagina.original in seleccion,
                        arrastrada = reordenacion.posicionArrastrada == posicion,
                        desplazamiento = reordenacion.desplazamiento,
                        reordenacion = reordenacion,
                        alAlternar = {
                            seleccion =
                                if (pagina.original in seleccion) {
                                    seleccion - pagina.original
                                } else {
                                    seleccion + pagina.original
                                }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Cabecera(
    seleccionadas: Int,
    quedan: Int,
    moviendo: Int?,
    alGuardar: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Organizar páginas",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                // Se dice cuántas quedan y no cuántas había: es lo que se va a guardar.
                text =
                    when {
                        // Mientras se mueve, la página que va en el aire se nombra: con
                        // el dedo encima de la miniatura, el número de debajo no se ve.
                        moviendo != null -> "Moviendo la p. ${moviendo + 1}"
                        seleccionadas == 0 -> "$quedan páginas · mantén pulsada una para moverla"
                        else -> "$seleccionadas de $quedan seleccionadas"
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(TAG_ORGANIZAR_RESUMEN),
            )
        }
        TextButton(onClick = alGuardar, modifier = Modifier.testTag(TAG_ORGANIZAR_GUARDAR)) { Text("Guardar") }
    }
}

@Composable
private fun AccionesDeOrganizar(
    haySeleccion: Boolean,
    puedeExtraer: Boolean,
    puedeEliminar: Boolean,
    alRotar: () -> Unit,
    alEliminar: () -> Unit,
    alExtraer: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(MedidasLadon.hueco),
    ) {
        AccionDeOrganizar(IconosLadon.rotar, "Girar", haySeleccion, TAG_ORGANIZAR_ROTAR, alRotar)
        AccionDeOrganizar(IconosLadon.eliminar, "Eliminar", puedeEliminar, TAG_ORGANIZAR_ELIMINAR, alEliminar)
        AccionDeOrganizar(IconosLadon.extraer, "Sólo éstas", puedeExtraer, TAG_ORGANIZAR_EXTRAER, alExtraer)
    }
}

@Composable
private fun AccionDeOrganizar(
    icono: Int,
    etiqueta: String,
    habilitada: Boolean,
    tag: String,
    alPulsar: () -> Unit,
) {
    TextButton(onClick = alPulsar, enabled = habilitada, modifier = Modifier.testTag(tag)) {
        IconoLadon(
            icono = icono,
            descripcion = null,
            // En acento como su propia etiqueta: el botón es una sola cosa y el icono
            // gris junto al texto rojo se leía como si estuviera medio apagado.
            estado = if (habilitada) EstadoIcono.ACENTO else EstadoIcono.DESHABILITADO,
        )
        Text(text = etiqueta, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
    }
}

/**
 * Una página en la rejilla: su miniatura girada como quedará, su número original y su
 * posición nueva.
 *
 * Los dos números están a propósito: al reordenar, «la 3» deja de estar en el tercer
 * hueco, y sin el número original no habría manera de saber qué página se está
 * moviendo.
 */
@Composable
private fun PaginaEnRejilla(
    pagina: PaginaOrdenada,
    posicion: Int,
    mapa: ImageBitmap?,
    seleccionada: Boolean,
    arrastrada: Boolean,
    desplazamiento: Offset,
    reordenacion: Reordenacion,
    alAlternar: () -> Unit,
) {
    // La posición cambia bajo los pies del gesto —es justo lo que el gesto hace— así
    // que se lee cuando hace falta y no se captura al empezar a escuchar: capturada,
    // el arrastre se creería siempre en el hueco del que salió.
    val dondeEstoy by rememberUpdatedState(posicion)
    var esquina by remember { mutableStateOf(Offset.Zero) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier
                .onGloballyPositioned {
                    esquina = it.positionInRoot()
                    reordenacion.anotarHueco(dondeEstoy, it.boundsInRoot())
                }
                // El gesto vive en la celda y no en la rejilla a propósito: en la
                // rejilla lo escuchaba antes el desplazamiento de la lista, que es su
                // hijo, y la pulsación larga no llegaba nunca.
                .pointerInput(pagina.original) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dentroDeLaCelda ->
                            reordenacion.empezar(dondeEstoy, esquina + dentroDeLaCelda)
                        },
                        onDrag = { cambio, delta ->
                            cambio.consume()
                            reordenacion.arrastrar(delta)
                        },
                        onDragEnd = reordenacion::soltar,
                        onDragCancel = reordenacion::soltar,
                    )
                }.graphicsLayer {
                    if (arrastrada) {
                        translationX = desplazamiento.x
                        translationY = desplazamiento.y
                        // La que va en el aire se levanta por encima de las demás.
                        shadowElevation = ELEVACION_ARRASTRE
                        scaleX = ESCALA_ARRASTRE
                        scaleY = ESCALA_ARRASTRE
                    }
                },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(PROPORCION_MINIATURA)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ColoresPapel.papel)
                    .then(
                        if (seleccionada) {
                            Modifier.border(
                                MedidasLadon.contornoSeleccion,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(3.dp),
                            )
                        } else {
                            Modifier
                        },
                    ).toggleable(value = seleccionada, onValueChange = { alAlternar() })
                    .testTag(tagPaginaOrganizar(pagina.original)),
            contentAlignment = Alignment.Center,
        ) {
            if (mapa != null) {
                Image(
                    bitmap = mapa,
                    contentDescription = null,
                    // El giro se enseña, no se promete: si se va a guardar de lado, se
                    // ve de lado antes de guardar.
                    modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = pagina.giro.toFloat() },
                    contentScale = ContentScale.Fit,
                )
            }
        }
        // El número que se enseña es el de la página **original**, no el del hueco: el
        // hueco ya se ve —es dónde está la celda— y lo que hace falta saber mientras se
        // reordena es qué página se está moviendo.
        Text(
            text = "p. ${pagina.original + 1}",
            style = MaterialTheme.typography.labelSmall,
            color =
                if (seleccionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(tagOrigenOrganizar(posicion)),
        )
    }
}

/** Un cuarto de vuelta más, dando la vuelta entera al llegar a 360. */
private fun PaginaOrdenada.girada() = copy(giro = (giro + GRADOS_POR_CUARTO) % VUELTA_ENTERA)

/**
 * El arrastre para reordenar.
 *
 * Reordena **mientras** se arrastra y no al soltar, que es lo que hace que el hueco se
 * abra bajo el dedo: el usuario ve dónde va a caer la página antes de decidir. El
 * desplazamiento se reinicia en cada intercambio porque la página ya está en su sitio
 * nuevo; sin eso, la miniatura se iría acumulando distancia y acabaría lejos del dedo.
 */
private class Reordenacion(
    private val alMover: (Int, Int) -> Unit,
) {
    var posicionArrastrada by mutableStateOf<Int?>(null)
        private set
    var desplazamiento by mutableStateOf(Offset.Zero)
        private set

    /**
     * Dónde cae cada hueco en la pantalla, según lo que dice cada celda de sí misma.
     *
     * Se pregunta a las celdas en vez de calcularlo desde la rejilla porque lo que
     * la rejilla sabe de sus items no está en el mismo sistema de coordenadas que el
     * dedo: sus posiciones cuentan desde dentro del margen y las del dedo desde el
     * borde. Ese desfase movía las páginas a una fila de distancia de donde se
     * soltaban.
     */
    private val huecos = mutableMapOf<Int, Rect>()
    private var punto = Offset.Zero

    fun anotarHueco(
        posicion: Int,
        donde: Rect,
    ) {
        huecos[posicion] = donde
    }

    /** Al quitar páginas sobran huecos: sus rectángulos ya no son de nadie. */
    fun olvidarSobrantes(cuantosQuedan: Int) {
        huecos.keys.retainAll { it < cuantosQuedan }
    }

    /** Coge la página del hueco [posicion]; el punto va en coordenadas de pantalla. */
    fun empezar(
        posicion: Int,
        enPantalla: Offset,
    ) {
        posicionArrastrada = posicion
        punto = enPantalla
        desplazamiento = Offset.Zero
    }

    fun arrastrar(delta: Offset) {
        val actual = posicionArrastrada ?: return
        desplazamiento += delta
        val destino = huecoEn(punto + desplazamiento) ?: return
        if (destino != actual) {
            alMover(actual, destino)
            posicionArrastrada = destino
            punto += desplazamiento
            desplazamiento = Offset.Zero
        }
    }

    fun soltar() {
        posicionArrastrada = null
        desplazamiento = Offset.Zero
    }

    /** Sobre qué hueco está el dedo, si está sobre alguno. */
    private fun huecoEn(donde: Offset): Int? = huecos.entries.firstOrNull { it.value.contains(donde) }?.key
}

private const val COLUMNAS = 3
private const val PROPORCION_MINIATURA = 0.72f
private const val ELEVACION_ARRASTRE = 12f
private const val ESCALA_ARRASTRE = 1.05f
private const val GRADOS_POR_CUARTO = 90
private const val VUELTA_ENTERA = 360

const val TAG_HOJA_ORGANIZAR = "hoja_organizar"
const val TAG_REJILLA_ORGANIZAR = "organizar_rejilla"
const val TAG_ORGANIZAR_RESUMEN = "organizar_resumen"
const val TAG_ORGANIZAR_GUARDAR = "organizar_guardar"
const val TAG_ORGANIZAR_ROTAR = "organizar_rotar"
const val TAG_ORGANIZAR_ELIMINAR = "organizar_eliminar"
const val TAG_ORGANIZAR_EXTRAER = "organizar_extraer"

/** La celda de una página, por el número que tenía en el documento original. */
fun tagPaginaOrganizar(original: Int): String = "organizar_pagina_$original"

/** Qué página original ocupa un hueco: lo que hay que mirar para ver el orden. */
fun tagOrigenOrganizar(posicion: Int): String = "organizar_origen_$posicion"
