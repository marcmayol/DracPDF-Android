package com.marcmayol.dracpdf.ui.visor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.Firma
import com.marcmayol.dracpdf.dominio.modelo.TamanoPt
import com.marcmayol.dracpdf.dominio.modelo.TipoCampo
import com.marcmayol.dracpdf.ui.firmas.FirmasViewModel
import com.marcmayol.dracpdf.ui.firmas.HojaDibujarFirma
import com.marcmayol.dracpdf.ui.firmas.HojaFirmas
import com.marcmayol.dracpdf.ui.herramientas.Herramienta
import com.marcmayol.dracpdf.ui.herramientas.HojaHerramientas
import com.marcmayol.dracpdf.ui.tema.ColoresPapel
import com.marcmayol.dracpdf.ui.tema.MedidasLadon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
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
    documentosAbiertos: Int = 1,
    alAbrirDocumentos: () -> Unit = {},
    alAbrirOtro: () -> Unit = {},
    alElegirHerramienta: (Herramienta) -> Unit = {},
    firmas: FirmasViewModel? = null,
) {
    val estado by modelo.estado.collectAsState()
    val paginas by modelo.paginas.collectAsState()
    val campos by modelo.campos.collectAsState()
    val lista = rememberLazyListState()

    // El zoom del gesto va aparte del zoom fijado: durante el pellizco sólo se estira
    // el bitmap que ya hay —gratis, lo hace la GPU— y el render nítido se pide cuando
    // el dedo se detiene. Rasterizar por fotograma daría un tirón en cada uno.
    var zoomVivo by remember { mutableFloatStateOf(estado.zoom) }
    var indiceAbierto by remember { mutableStateOf(false) }
    var saltarA by remember { mutableStateOf<Int?>(null) }
    var opcionesDe by remember { mutableStateOf<CampoFormulario?>(null) }
    var firmasAbiertas by remember { mutableStateOf(false) }
    var herramientasAbiertas by remember { mutableStateOf(false) }
    var dibujando by remember { mutableStateOf(false) }

    // La imagen de la firma que se está colocando sale de la misma caché de
    // miniaturas de la biblioteca: ya está decodificada y no hace falta otra copia.
    val miniaturasDeFirmas =
        firmas
            ?.miniaturas
            ?.collectAsState()
            ?.value
            .orEmpty()
    val firmaEnMano = estado.colocacion?.let { miniaturasDeFirmas[it.firma.id.valor] }
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
                .background(MaterialTheme.colorScheme.background)
                // El teclado del formulario levanta la barra del modo en vez de
                // taparla. Es la queja número uno de rellenar formularios en el móvil,
                // y se resuelve aquí, una vez, y no campo a campo.
                .imePadding(),
    ) {
        AnimatedVisibility(visible = estado.chromeVisible, enter = fadeIn(), exit = fadeOut()) {
            BarraDeArriba(
                estado = estado,
                modelo = modelo,
                alSalir = alSalir,
                documentosAbiertos = documentosAbiertos,
                alAbrirDocumentos = alAbrirDocumentos,
                alAbrirOtro = alAbrirOtro,
            )
        }

        estado.aviso?.let { aviso ->
            BandaAvisoFormulario(aviso = aviso, alDescartar = modelo::descartarAviso)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ListaDePaginas(
                estado = estado,
                paginas = paginas,
                campos = campos,
                lista = lista,
                zoomVivo = zoomVivo,
                tamanoDe = modelo::tamanoDe,
                // Cada tipo de campo se toca de una manera: una casilla cambia sola,
                // una elección abre su lista, y un texto se pone a esperar teclas.
                alTocarCampo = { campo -> tocarCampo(modelo, campo) { opcionesDe = campo } },
                alEscribirCampo = { campo, valor -> modelo.escribirTexto(campo.id, valor) },
                alIrAlSiguiente = { modelo.irACampo(Direccion.SIGUIENTE) },
                firmaEnMano = firmaEnMano,
                alArrastrarFirma = modelo::moverColocacion,
                alRedimensionarFirma = modelo::redimensionarColocacion,
                alTocar = modelo::alternarChrome,
                // Los dos gestos de zoom devuelven cuánto ha crecido de verdad la
                // página —el factor pedido, ya recortado contra los topes—, porque es lo
                // que necesita la lista para dejar quieto el punto que se está mirando.
                alDobleTocar = {
                    val anterior = zoomVivo
                    modelo.fijarZoom(if (estado.zoom > AJUSTE_ANCHO) AJUSTE_ANCHO else CIEN_POR_CIEN)
                    zoomVivo = modelo.estado.value.zoom
                    zoomVivo / anterior
                },
                alPellizcar = { factor ->
                    val anterior = zoomVivo
                    zoomVivo = (anterior * factor).coerceIn(VisorViewModel.ZOOM_MINIMO, VisorViewModel.ZOOM_MAXIMO)
                    zoomVivo / anterior
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
            BarraDelModo(
                modo = estado.modo,
                alAbrirIndice = { indiceAbierto = true },
                alEntrarEnFormulario = modelo::entrarEnFormulario,
                alAbrirHerramientas = { herramientasAbiertas = true },
                alCampoAnterior = { modelo.irACampo(Direccion.ANTERIOR) },
                alCampoSiguiente = { modelo.irACampo(Direccion.SIGUIENTE) },
                alConfirmarColocacion = modelo::confirmarColocacion,
                alCancelarColocacion = modelo::cancelarColocacion,
                alAbrirFirmas = {
                    firmas?.cargar()
                    firmasAbiertas = true
                },
                hayCampoAnterior = estado.hayCampoAnterior,
                hayCampoSiguiente = estado.hayCampoSiguiente,
                campos = estado.formulario?.campos ?: 0,
                formularioDisponible = estado.hayFormulario,
                cambiosSinGuardar = estado.cambiosSinGuardar,
                guardando = estado.guardando,
            )
        }
    }

    estado.error?.let { error ->
        BandaError(mensaje = error, alDescartar = modelo::descartarError)
    }

    HojasDeFirmas(
        firmas = firmas,
        biblioteca = firmasAbiertas,
        dibujando = dibujando,
        alCerrarBiblioteca = { firmasAbiertas = false },
        alCerrarDibujo = { dibujando = false },
        alDibujarNueva = {
            firmasAbiertas = false
            dibujando = true
        },
        alColocar = { firma ->
            firmasAbiertas = false
            dibujando = false
            modelo.empezarAColocar(firma)
        },
    )

    opcionesDe?.let { campo ->
        HojaOpciones(
            campo = campo,
            alElegir = { opcion ->
                modelo.elegirOpcion(campo.id, opcion)
                opcionesDe = null
            },
            alCerrar = { opcionesDe = null },
        )
    }

    if (herramientasAbiertas) {
        HojaHerramientas(
            alElegir = { herramienta ->
                herramientasAbiertas = false
                alElegirHerramienta(herramienta)
            },
            alCerrar = { herramientasAbiertas = false },
            documentoFirmado = estado.firmado,
        )
    }

    if (indiceAbierto) {
        val miniaturas by modelo.miniaturas.collectAsState()
        HojaIndice(
            paginas = estado.paginas,
            paginaActual = estado.paginaActual,
            miniaturas = miniaturas,
            alPedirMiniatura = modelo::pedirMiniatura,
            alElegirPagina = { pagina ->
                modelo.irAPagina(pagina)
                indiceAbierto = false
                saltarA = pagina
            },
            alCerrar = { indiceAbierto = false },
        )
    }

    LaunchedEffect(saltarA) {
        saltarA?.let { pagina ->
            lista.scrollToItem(pagina)
            saltarA = null
        }
    }

    TraerElCampoALaVista(modelo = modelo, lista = lista, estado = estado, zoom = zoomVivo)
}

/**
 * La barra superior que toca: la del documento, o la del modo que esté en pie.
 *
 * **Una y sólo una.** El diseño no deja que convivan, y por eso se deciden en un solo
 * sitio en vez de que cada modo añada la suya encima de la anterior.
 */
@Composable
private fun BarraDeArriba(
    estado: EstadoVisor,
    modelo: VisorViewModel,
    alSalir: () -> Unit,
    documentosAbiertos: Int,
    alAbrirDocumentos: () -> Unit,
    alAbrirOtro: () -> Unit,
) {
    when (estado.modo) {
        ModoVisor.Formulario ->
            BarraSuperiorDelModo(
                titulo = "Formulario",
                accion = if (estado.guardando) "Guardando…" else "Guardar",
                alCerrar = modelo::salirDelFormulario,
                alAccionar = modelo::guardar,
                // Sin cambios no hay nada que guardar, y guardar dos veces lo mismo
                // dejaría una revisión vacía en el fichero.
                accionHabilitada = estado.cambiosSinGuardar && !estado.guardando,
            )

        // La colocación de una firma conserva la barra del documento mientras se
        // decide dónde va: sus dos controles viven abajo, junto al pulgar que arrastra.
        ModoVisor.Lectura, ModoVisor.ColocarFirma ->
            BarraSuperiorVisor(
                nombre = estado.nombre,
                alSalir = alSalir,
                documentosAbiertos = documentosAbiertos,
                alAbrirDocumentos = alAbrirDocumentos,
                alAbrirOtro = alAbrirOtro,
            )
    }
}

/**
 * Qué hace un toque en un campo, según de qué campo se trate: una casilla cambia
 * sola, una elección abre su lista, y un texto se queda esperando teclas.
 */
private fun tocarCampo(
    modelo: VisorViewModel,
    campo: CampoFormulario,
    alPedirOpciones: () -> Unit,
) {
    modelo.activarCampo(campo.id)
    when (campo.tipo) {
        TipoCampo.CASILLA, TipoCampo.RADIO -> modelo.alternarCampo(campo.id)
        TipoCampo.COMBO, TipoCampo.LISTA -> alPedirOpciones()
        else -> Unit
    }
}

/**
 * Trae el campo enfocado a la vista.
 *
 * Se deja por encima de la mitad de la pantalla a propósito: ahí es donde no lo tapa
 * el teclado al abrirse, que es el fallo más repetido de los formularios en el móvil.
 */
@Composable
private fun TraerElCampoALaVista(
    modelo: VisorViewModel,
    lista: LazyListState,
    estado: EstadoVisor,
    zoom: Float,
) {
    val desplazar by modelo.desplazarA.collectAsState()
    val altoPantalla = LocalConfiguration.current.screenHeightDp

    LaunchedEffect(desplazar) {
        desplazar?.let { destino ->
            val altoPagina = altoDePaginaDe(altoPantalla, estado, zoom)
            val dentroDeLaPagina = destino.fraccionY * altoPagina
            lista.scrollToItem(destino.pagina, (dentroDeLaPagina - altoPagina * MARGEN_SOBRE_EL_TECLADO).toInt())
            modelo.desplazamientoAtendido()
        }
    }
}

/**
 * Cuánto mide de alto la página en píxeles, para saber dónde cae un campo dentro de
 * ella. Es una estimación: basta para dejar el campo a la vista, y quien afina el
 * resto es el propio scroll.
 */
private fun altoDePaginaDe(
    altoPantallaDp: Int,
    estado: EstadoVisor,
    zoom: Float,
): Float {
    val proporcion = estado.tamanoEstimado?.let { it.alto / it.ancho } ?: PROPORCION_A4
    return altoPantallaDp * proporcion * zoom
}

@Composable
private fun ListaDePaginas(
    estado: EstadoVisor,
    paginas: Map<ClavePagina, ImageBitmap>,
    campos: Map<Int, List<CampoFormulario>>,
    lista: LazyListState,
    zoomVivo: Float,
    tamanoDe: (Int) -> TamanoPt?,
    alTocarCampo: (CampoFormulario) -> Unit,
    alEscribirCampo: (CampoFormulario, String) -> Unit,
    alIrAlSiguiente: () -> Unit,
    firmaEnMano: ImageBitmap?,
    alArrastrarFirma: (Float, Float) -> Unit,
    alRedimensionarFirma: (Float) -> Unit,
    alTocar: () -> Unit,
    alDobleTocar: () -> Float,
    alPellizcar: (Float) -> Float,
) {
    val scrollHorizontal = rememberScrollState()
    val ajustePendiente = remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val anchoPagina: Dp = maxWidth * zoomVivo

        AplicarElAnclaje(pendiente = ajustePendiente, lista = lista, scrollHorizontal = scrollHorizontal)

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Los gestos van por fuera del scroll horizontal a propósito: así
                    // reciben las coordenadas de la pantalla y no las del contenido ya
                    // desplazado, que es lo que hace falta para anclar el zoom.
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { alTocar() },
                            onDoubleTap = { donde ->
                                ajustePendiente.value += anclaje(donde, alDobleTocar(), lista, scrollHorizontal)
                            },
                        )
                    }.pointerInput(Unit) {
                        // El pellizco es de dos dedos y sólo entonces se queda el gesto.
                        // Con `detectTransformGestures` el arrastre de un dedo también
                        // se consumía aquí, y la página ampliada no se podía mover: se
                        // veía el zoom y no había manera de llegar al margen derecho.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var evento: PointerEvent
                            do {
                                evento = awaitPointerEvent(PointerEventPass.Initial)
                                if (evento.changes.count { it.pressed } >= 2) {
                                    val factor = evento.calculateZoom()
                                    if (factor != 1f) {
                                        val centro = evento.calculateCentroid(useCurrent = true)
                                        val real = alPellizcar(factor)
                                        if (centro.isSpecified) {
                                            ajustePendiente.value += anclaje(centro, real, lista, scrollHorizontal)
                                        }
                                    }
                                    // Con dos dedos el gesto es nuestro: si no se
                                    // consume, la lista lee el movimiento como scroll y
                                    // el documento se va solo mientras se amplía.
                                    evento.changes.forEach { it.consume() }
                                }
                            } while (evento.changes.any { it.pressed })
                        }
                    }
                    // Con zoom la página es más ancha que la pantalla, así que la
                    // columna entera se desplaza en horizontal.
                    .then(if (zoomVivo > 1f) Modifier.horizontalScroll(scrollHorizontal) else Modifier),
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
                    ) {
                        // El overlay sólo existe dentro del modo y sólo para las
                        // páginas que están a la vista: es el mismo trato perezoso que
                        // reciben los píxeles, y no hay nada que guardar al soltarlo
                        // porque los valores viven en el documento.
                        val colocacion = estado.colocacion?.takeIf { it.pagina == indice }
                        if (colocacion != null && firmaEnMano != null && tamano != null) {
                            OverlayColocacion(
                                firma = firmaEnMano,
                                colocacion = colocacion,
                                tamano = tamano,
                                ancho = anchoPagina,
                                alto = anchoPagina * proporcion,
                                alArrastrar = alArrastrarFirma,
                                alRedimensionar = alRedimensionarFirma,
                            )
                        }

                        val delaPagina = campos[indice]
                        if (estado.modo == ModoVisor.Formulario && tamano != null && !delaPagina.isNullOrEmpty()) {
                            OverlayCampos(
                                campos = delaPagina,
                                tamano = tamano,
                                ancho = anchoPagina,
                                alto = anchoPagina * proporcion,
                                campoActivo = estado.campoActivo,
                                alTocarCampo = alTocarCampo,
                                alEscribir = alEscribirCampo,
                                hayCampoSiguiente = estado.hayCampoSiguiente,
                                alIrAlSiguiente = alIrAlSiguiente,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Cuánto hay que desplazar la vista para que el punto que se está ampliando se quede
 * donde estaba.
 *
 * Sin esto el zoom crece desde la esquina de arriba a la izquierda y lo que se estaba
 * mirando se escapa de la pantalla, que es el defecto clásico del zoom en un visor.
 * Se calcula **antes** de que la página se vuelva a medir, con la posición que el
 * contenido tiene todavía.
 */
private fun anclaje(
    centro: Offset,
    factorReal: Float,
    lista: LazyListState,
    scrollHorizontal: ScrollState,
): Offset {
    if (!factorReal.isFinite() || factorReal == 1f) return Offset.Zero

    val crecimiento = factorReal - 1f
    val visibles = lista.layoutInfo.visibleItemsInfo
    // La página bajo el dedo, y si el dedo cae en un hueco, la primera visible: el
    // desplazamiento se mide desde el borde de esa página, no desde el principio del
    // documento, que con 500 páginas ni se conoce ni haría falta.
    val pagina =
        visibles.firstOrNull { centro.y >= it.offset && centro.y < it.offset + it.size }
            ?: visibles.firstOrNull()

    return Offset(
        x = (scrollHorizontal.value + centro.x) * crecimiento,
        y = if (pagina == null) 0f else (centro.y - pagina.offset) * crecimiento,
    )
}

/**
 * Aplica el anclaje acumulado, un frame después de pedirlo.
 *
 * La espera no es un apaño: en el momento del gesto la página todavía mide lo que
 * medía, y desplazarse ahí se recortaría contra el ancho viejo —la vista se pegaría al
 * margen izquierdo—. Los ajustes se suman mientras tanto, así que un pellizco largo no
 * pierde ninguno por el camino.
 */
@Composable
private fun AplicarElAnclaje(
    pendiente: MutableState<Offset>,
    lista: LazyListState,
    scrollHorizontal: ScrollState,
) {
    LaunchedEffect(lista, scrollHorizontal) {
        snapshotFlow { pendiente.value }
            .filter { it != Offset.Zero }
            .collect { ajuste ->
                pendiente.value = Offset.Zero
                withFrameNanos { }
                if (ajuste.y != 0f) lista.scrollBy(ajuste.y)
                if (ajuste.x != 0f) scrollHorizontal.scrollBy(ajuste.x)
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
    encima: @Composable () -> Unit = {},
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
        encima()
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

/**
 * Las dos hojas de firmas: la biblioteca y el lienzo.
 *
 * Se saca aquí para que la pantalla del visor siga cabiendo en la cabeza de quien la
 * lee; el visor ya tiene bastante con los modos.
 */
@Composable
private fun HojasDeFirmas(
    firmas: FirmasViewModel?,
    biblioteca: Boolean,
    dibujando: Boolean,
    alCerrarBiblioteca: () -> Unit,
    alCerrarDibujo: () -> Unit,
    alDibujarNueva: () -> Unit,
    alColocar: (Firma) -> Unit,
) {
    if (firmas == null) return

    if (biblioteca) {
        val guardadas by firmas.firmas.collectAsState()
        val miniaturas by firmas.miniaturas.collectAsState()
        HojaFirmas(
            firmas = guardadas,
            miniaturas = miniaturas,
            alElegir = alColocar,
            alBorrar = firmas::borrar,
            alDibujarNueva = alDibujarNueva,
            alCerrar = alCerrarBiblioteca,
        )
    }

    if (dibujando) {
        HojaDibujarFirma(
            alGuardar = { dibujada ->
                alCerrarDibujo()
                // Recién dibujada, lo que se quiere es usarla: se guarda y se pasa
                // directamente a colocarla, sin volver a la lista a buscarla.
                firmas.guardar(dibujada, alGuardar = alColocar)
            },
            alCerrar = alCerrarDibujo,
        )
    }
}

private const val PROPORCION_A4 = 842f / 595f
private const val OPACIDAD_PILDORA = 0.92f
private const val AJUSTE_ANCHO = 1f

/** El «100 %» del doble toque: el doble del ajuste a ancho, que en A4 deja el texto legible. */
private const val CIEN_POR_CIEN = 2f

private const val ESPERA_TRAS_EL_GESTO_MS = 180L

/** Lo que se deja de página por encima del campo enfocado: un tercio de la altura. */
private const val MARGEN_SOBRE_EL_TECLADO = 0.33f

const val TAG_LISTA = "visor_lista"
const val TAG_PILDORA = "visor_pildora_pagina"

fun tagPagina(indice: Int): String = "visor_pagina_$indice"
