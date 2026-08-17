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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.DestinoEnlace
import com.marcmayol.dracpdf.dominio.modelo.Firma
import com.marcmayol.dracpdf.dominio.modelo.PuntoPt
import com.marcmayol.dracpdf.dominio.modelo.TamanoPt
import com.marcmayol.dracpdf.dominio.modelo.TipoCampo
import com.marcmayol.dracpdf.dominio.modelo.contiene
import com.marcmayol.dracpdf.ui.compartir.Compartir
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
    /** Imprimir y compartir salen de aquí porque necesitan el fichero, no el documento. */
    alImprimir: (() -> Unit)? = null,
    alCompartirDocumento: (() -> Unit)? = null,
) {
    val estado by modelo.estado.collectAsState()
    val paginas by modelo.paginas.collectAsState()
    val campos by modelo.campos.collectAsState()
    val enlaces by modelo.enlaces.collectAsState()
    val lista = rememberLazyListState()
    val portapapeles = LocalClipboardManager.current
    val contexto = LocalContext.current

    // El zoom del gesto va aparte del zoom fijado: durante el pellizco sólo se estira
    // el bitmap que ya hay —gratis, lo hace la GPU— y el render nítido se pide cuando
    // el dedo se detiene. Rasterizar por fotograma daría un tirón en cada uno.
    var zoomVivo by remember { mutableFloatStateOf(estado.zoom) }
    var indiceAbierto by remember { mutableStateOf(false) }
    var vistaAbierta by remember { mutableStateOf(false) }
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

    // La caja de fuera existe para una sola pregunta: **cuánto ancho hay de verdad**.
    // De ahí sale si se pueden ofrecer dos páginas, y se decide con el ancho y no con
    // el modelo del aparato porque el mismo teléfono no cabe de pie y sí cabe tumbado,
    // y una ventana a media pantalla no es «una tablet» por mucho que corra en una.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cabenDos = maxWidth >= ANCHO_MINIMO_PARA_DOS
        val porFila = estado.vista.paginasPorFila(cabenDos)

        LaunchedEffect(lista, porFila) {
            snapshotFlow {
                val visibles = lista.layoutInfo.visibleItemsInfo
                if (visibles.isEmpty()) null else visibles.first().index to visibles.last().index
            }.collect { ventana ->
                // La lista cuenta filas y el modelo cuenta páginas: con la doble página
                // puesta, la fila 3 es la página 6.
                ventana?.let { (primera, ultima) ->
                    modelo.alCambiarVentana(primera * porFila, ultima * porFila + porFila - 1)
                }
            }
        }

        ConservarLaPagina(porFila = porFila, pagina = estado.paginaActual, lista = lista)

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    // El teclado del formulario levanta la barra del modo en vez de
                    // taparla. Es la queja número uno de rellenar formularios en el
                    // móvil, y se resuelve aquí, una vez, y no campo a campo.
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
                    alImprimir = alImprimir,
                    alCompartirDocumento = alCompartirDocumento,
                    alAjustarLaVista = { vistaAbierta = true },
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
                    porFila = porFila,
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
                    // página —el factor pedido, ya recortado contra los topes—, porque es
                    // lo que necesita la lista para dejar quieto el punto que se mira.
                    alDobleTocar = {
                        val anterior = zoomVivo
                        modelo.fijarZoom(if (estado.zoom > SIN_ZOOM) SIN_ZOOM else EL_DOBLE)
                        zoomVivo = modelo.estado.value.zoom
                        zoomVivo / anterior
                    },
                    alPellizcar = { factor ->
                        val anterior = zoomVivo
                        zoomVivo =
                            (anterior * factor).coerceIn(VisorViewModel.ZOOM_MINIMO, VisorViewModel.ZOOM_MAXIMO)
                        zoomVivo / anterior
                    },
                    alSeleccionar = modelo::seleccionarPalabraEn,
                    alMoverAsa = modelo::moverAsa,
                    enlaceEn = { pagina, punto ->
                        enlaces[pagina]?.firstOrNull { it.marco.contiene(punto) }?.destino
                    },
                    alSeguirEnlace = modelo::seguirEnlace,
                )

                PildoraPagina(
                    estado = estado,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                )
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
                    alCopiar = {
                        estado.seleccion
                            ?.seleccion
                            ?.texto
                            ?.let { portapapeles.setText(AnnotatedString(it)) }
                        modelo.soltarSeleccion()
                    },
                    alCompartir = {
                        estado.seleccion
                            ?.seleccion
                            ?.texto
                            ?.let { Compartir.texto(contexto, it) }
                    },
                    alDeshacer = modelo::deshacer,
                    hayQueDeshacer = estado.hayQueDeshacer,
                    hayCampoAnterior = estado.hayCampoAnterior,
                    hayCampoSiguiente = estado.hayCampoSiguiente,
                    campos = estado.formulario?.campos ?: 0,
                    formularioDisponible = estado.hayFormulario,
                    cambiosSinGuardar = estado.cambiosSinGuardar,
                    guardando = estado.guardando,
                )
            }
        }

        if (vistaAbierta) {
            HojaVista(
                vista = estado.vista,
                cabenDosPaginas = cabenDos,
                alAjustar = modelo::ajustarLaVista,
                alAlternarDoblePagina = modelo::alternarDoblePagina,
                alGirar = modelo::girarLaVista,
                alCerrar = { vistaAbierta = false },
            )
        }

        LaunchedEffect(saltarA) {
            saltarA?.let { pagina ->
                lista.scrollToItem(pagina / porFila)
                saltarA = null
            }
        }

        TraerElCampoALaVista(
            modelo = modelo,
            lista = lista,
            estado = estado,
            zoom = zoomVivo,
            porFila = porFila,
        )
    }

    DialogosDelDocumento(estado = estado, modelo = modelo)

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
        HojaDelIndice(
            estado = estado,
            modelo = modelo,
            alElegirPagina = { pagina ->
                indiceAbierto = false
                saltarA = pagina
            },
            alCerrar = { indiceAbierto = false },
        )
    }
}

/**
 * Lo que el documento tiene que decir por encima de todo lo demás: un error, un enlace
 * que sale fuera y espera un sí, o su ficha.
 *
 * Van juntos y fuera de la pantalla porque no dependen de nada suyo —sólo del estado y
 * del modelo— y porque dentro engordaban el visor con tres ramas que no tienen que ver
 * con enseñar páginas.
 */
@Composable
private fun DialogosDelDocumento(
    estado: EstadoVisor,
    modelo: VisorViewModel,
) {
    val contexto = LocalContext.current

    estado.error?.let { error ->
        BandaError(mensaje = error, alDescartar = modelo::descartarError)
    }

    estado.enlacePreguntado?.let { url ->
        DialogoEnlace(
            url = url,
            alAbrir = {
                modelo.olvidarEnlace()
                Compartir.abrirEnlace(contexto, url)
            },
            alCancelar = modelo::olvidarEnlace,
        )
    }

    estado.propiedades?.let { ficha ->
        DialogoPropiedades(propiedades = ficha, alCerrar = modelo::cerrarPropiedades)
    }
}

/**
 * El índice del documento, con sus miniaturas.
 *
 * Se pide al abrir la hoja y no al abrir el documento: en uno de quinientas páginas es
 * un recorrido que casi nadie va a mirar.
 */
@Composable
private fun HojaDelIndice(
    estado: EstadoVisor,
    modelo: VisorViewModel,
    alElegirPagina: (Int) -> Unit,
    alCerrar: () -> Unit,
) {
    val miniaturas by modelo.miniaturas.collectAsState()
    LaunchedEffect(Unit) { modelo.cargarIndice() }
    HojaIndice(
        paginas = estado.paginas,
        paginaActual = estado.paginaActual,
        indice = estado.indice,
        miniaturas = miniaturas,
        alPedirMiniatura = modelo::pedirMiniatura,
        alElegirPagina = { pagina ->
            modelo.irAPagina(pagina)
            alElegirPagina(pagina)
        },
        alCerrar = alCerrar,
    )
}

/**
 * Deja al lector donde estaba cuando cambia cuántas páginas van por fila.
 *
 * La lista guarda su sitio por número de fila, y al pasar de una página por fila a dos
 * la fila 40 deja de ser la página 40 para ser la 80. Sin esto, encender la doble
 * página teletransporta a mitad del documento, que es la clase de fallo que hace
 * desconfiar de la opción entera.
 *
 * La primera composición no cuenta: ahí no se ha cambiado nada, y desplazarse pisaría
 * la página por la que el documento se estaba abriendo.
 */
@Composable
private fun ConservarLaPagina(
    porFila: Int,
    pagina: Int,
    lista: LazyListState,
) {
    var anterior by remember { mutableIntStateOf(porFila) }
    LaunchedEffect(porFila) {
        if (porFila != anterior) {
            anterior = porFila
            lista.scrollToItem(pagina / porFila)
        }
    }
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
    alImprimir: (() -> Unit)?,
    alCompartirDocumento: (() -> Unit)?,
    alAjustarLaVista: () -> Unit,
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

        ModoVisor.Buscar ->
            BarraDeBusqueda(
                busqueda = estado.busqueda ?: Busqueda(),
                alEscribir = modelo::buscar,
                alAnterior = { modelo.irACoincidencia(Direccion.ANTERIOR) },
                alSiguiente = { modelo.irACoincidencia(Direccion.SIGUIENTE) },
                alCerrar = modelo::cerrarBusqueda,
            )

        ModoVisor.SeleccionarTexto ->
            BarraSuperiorDelModo(
                titulo = "Selección",
                accion = "Hecho",
                alCerrar = modelo::soltarSeleccion,
                alAccionar = modelo::soltarSeleccion,
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
                alBuscar = modelo::abrirBusqueda,
                alImprimir = alImprimir,
                alCompartir = alCompartirDocumento,
                alVerPropiedades = modelo::verPropiedades,
                alAjustarLaVista = alAjustarLaVista,
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
    porFila: Int,
) {
    val desplazar by modelo.desplazarA.collectAsState()
    val altoPantalla = LocalConfiguration.current.screenHeightDp

    LaunchedEffect(desplazar) {
        desplazar?.let { destino ->
            val altoPagina = altoDePaginaDe(altoPantalla, estado, zoom)
            val dentroDeLaPagina = destino.fraccionY * altoPagina
            lista.scrollToItem(
                destino.pagina / porFila,
                (dentroDeLaPagina - altoPagina * MARGEN_SOBRE_EL_TECLADO).toInt(),
            )
            modelo.desplazamientoAtendido()
        }
    }
}

@Composable
private fun ListaDePaginas(
    estado: EstadoVisor,
    paginas: Map<ClavePagina, ImageBitmap>,
    campos: Map<Int, List<CampoFormulario>>,
    lista: LazyListState,
    zoomVivo: Float,
    porFila: Int,
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
    alSeleccionar: (Int, PuntoPt) -> Unit,
    alMoverAsa: (Boolean, PuntoPt) -> Unit,
    enlaceEn: (Int, PuntoPt) -> DestinoEnlace?,
    alSeguirEnlace: (DestinoEnlace) -> Unit,
) {
    val scrollHorizontal = rememberScrollState()
    val ajustePendiente = remember { mutableStateOf(Offset.Zero) }
    val giro = estado.vista.giro

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val proporcionDelDocumento = estado.tamanoEstimado?.let { it.alto / it.ancho } ?: PROPORCION_A4
        val anchoPagina: Dp =
            anchoDeLaPagina(
                disponible = maxWidth,
                altoDisponible = maxHeight,
                proporcion = proporcionDelDocumento,
                vista = estado.vista,
                porFila = porFila,
            ) * zoomVivo
        val anchoFila = anchoPagina * porFila + MedidasLadon.hueco * (porFila - 1)
        val filas = (estado.paginas + porFila - 1) / porFila

        val maqueta =
            with(LocalDensity.current) {
                MaquetaDeLaLista(
                    anchoPagina = anchoPagina.toPx(),
                    hueco = MedidasLadon.hueco.toPx(),
                    margenIzquierdo = maxOf(0.dp, (maxWidth - anchoFila) / 2).toPx(),
                    porFila = porFila,
                    giro = giro,
                    paginas = estado.paginas,
                )
            }

        // La maqueta cambia con cada fotograma de un pellizco. Se lee por referencia y
        // no se pasa como clave del `pointerInput`: reiniciar el detector de gestos a
        // media ampliación se comería el toque que lo empezó.
        val maquetaViva = rememberUpdatedState(maqueta)

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
                            // Un toque puede ser tres cosas, y se deciden aquí porque
                            // este es el único punto que sabe traducir la pantalla a
                            // página: seguir un enlace, apagar el chrome, o nada.
                            onTap = { donde ->
                                val enLaPagina =
                                    paginaYPunto(donde, lista, scrollHorizontal, tamanoDe, maquetaViva.value)
                                val enlace = enLaPagina?.let { (pagina, punto) -> enlaceEn(pagina, punto) }
                                if (enlace == null) alTocar() else alSeguirEnlace(enlace)
                            },
                            onLongPress = { donde ->
                                paginaYPunto(donde, lista, scrollHorizontal, tamanoDe, maquetaViva.value)
                                    ?.let { (pagina, punto) -> alSeleccionar(pagina, punto) }
                            },
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
                    // Con zoom, o con la página entera de lado, la fila puede ser más
                    // ancha que la pantalla: entonces —y sólo entonces— se desplaza en
                    // horizontal. A página completa nunca sobra, y el scroll estorbaría.
                    .then(if (anchoFila > maxWidth) Modifier.horizontalScroll(scrollHorizontal) else Modifier),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = lista,
                modifier = Modifier.width(anchoFila).fillMaxSize().testTag(TAG_LISTA),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MedidasLadon.hueco),
                contentPadding = PaddingValues(vertical = PADDING_VERTICAL),
            ) {
                items(count = filas, key = { fila -> fila }) { fila ->
                    FilaDePaginas(
                        fila = fila,
                        porFila = porFila,
                        anchoPagina = anchoPagina,
                        giro = giro,
                        estado = estado,
                        dibujadas = paginas,
                        zoom = zoomVivo,
                        tamanoDe = tamanoDe,
                    ) { indice, caja ->
                        ContenidoDeLaPagina(
                            indice = indice,
                            caja = caja,
                            estado = estado,
                            campos = campos,
                            tamanoDe = tamanoDe,
                            firmaEnMano = firmaEnMano,
                            acciones =
                                AccionesSobreLaPagina(
                                    alTocarCampo = alTocarCampo,
                                    alEscribirCampo = alEscribirCampo,
                                    alIrAlSiguiente = alIrAlSiguiente,
                                    alArrastrarFirma = alArrastrarFirma,
                                    alRedimensionarFirma = alRedimensionarFirma,
                                    alMoverAsa = alMoverAsa,
                                ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Una fila de la lista: una página, o dos cuando la pantalla da para ello.
 *
 * El hueco vacío de la última fila impar se reserva igual, en vez de centrar la página
 * suelta: en un libro la última hoja tampoco se coloca en medio, y verla saltar al
 * centro al llegar al final parece un fallo de maquetación.
 */
@Composable
private fun FilaDePaginas(
    fila: Int,
    porFila: Int,
    anchoPagina: Dp,
    giro: GiroDeVista,
    estado: EstadoVisor,
    dibujadas: Map<ClavePagina, ImageBitmap>,
    zoom: Float,
    tamanoDe: (Int) -> TamanoPt?,
    encima: @Composable (Int, CajaDePagina) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(MedidasLadon.hueco)) {
        repeat(porFila) { columna ->
            val indice = fila * porFila + columna
            if (indice >= estado.paginas) {
                Spacer(modifier = Modifier.width(anchoPagina))
            } else {
                val tamano = tamanoDe(indice)
                val proporcion = if (tamano == null) PROPORCION_A4 else tamano.alto / tamano.ancho
                val caja = cajaDePagina(anchoPagina, proporcion, giro)
                PaginaDelDocumento(
                    indice = indice,
                    mapa = mejorDisponible(dibujadas, indice, zoom),
                    caja = caja,
                    giro = giro,
                ) {
                    encima(indice, caja)
                }
            }
        }
    }
}

/**
 * Lo que cada gesto de un overlay le pide al modelo.
 *
 * Van juntas porque siempre viajan juntas hasta la página, y sueltas convertían la
 * llamada en una fila de lambdas donde el orden era lo único que las distinguía.
 */
private class AccionesSobreLaPagina(
    val alTocarCampo: (CampoFormulario) -> Unit,
    val alEscribirCampo: (CampoFormulario, String) -> Unit,
    val alIrAlSiguiente: () -> Unit,
    val alArrastrarFirma: (Float, Float) -> Unit,
    val alRedimensionarFirma: (Float) -> Unit,
    val alMoverAsa: (Boolean, PuntoPt) -> Unit,
)

/**
 * Lo que se dibuja **encima** del papel de una página.
 *
 * Todos hablan en puntos de página y reciben el tamaño **sin girar**: van dentro de la
 * caja que gira entera, así que ninguno tiene que enterarse de que la vista está de
 * lado. Es la razón de que el giro se aplique a la caja y no al bitmap.
 */
@Composable
private fun ContenidoDeLaPagina(
    indice: Int,
    caja: CajaDePagina,
    estado: EstadoVisor,
    campos: Map<Int, List<CampoFormulario>>,
    tamanoDe: (Int) -> TamanoPt?,
    firmaEnMano: ImageBitmap?,
    acciones: AccionesSobreLaPagina,
) {
    val tamano = tamanoDe(indice) ?: return

    val colocacion = estado.colocacion?.takeIf { it.pagina == indice }
    if (colocacion != null && firmaEnMano != null) {
        OverlayColocacion(
            firma = firmaEnMano,
            colocacion = colocacion,
            tamano = tamano,
            ancho = caja.ancho,
            alto = caja.alto,
            alArrastrar = acciones.alArrastrarFirma,
            alRedimensionar = acciones.alRedimensionarFirma,
        )
    }

    // El overlay sólo existe dentro del modo y sólo para las páginas que están a la
    // vista: es el mismo trato perezoso que reciben los píxeles, y no hay nada que
    // guardar al soltarlo porque los valores viven en el documento.
    val delaPagina = campos[indice]
    if (estado.modo == ModoVisor.Formulario && !delaPagina.isNullOrEmpty()) {
        OverlayCampos(
            campos = delaPagina,
            tamano = tamano,
            ancho = caja.ancho,
            alto = caja.alto,
            campoActivo = estado.campoActivo,
            alTocarCampo = acciones.alTocarCampo,
            alEscribir = acciones.alEscribirCampo,
            hayCampoSiguiente = estado.hayCampoSiguiente,
            alIrAlSiguiente = acciones.alIrAlSiguiente,
        )
    }

    val enEstaPagina = estado.busqueda?.enLaPagina(indice).orEmpty()
    if (enEstaPagina.isNotEmpty()) {
        OverlayResaltados(
            coincidencias = enEstaPagina.map { it.marco },
            activa =
                estado.busqueda
                    ?.coincidencias
                    ?.getOrNull(estado.busqueda.activa)
                    ?.takeIf { it.pagina == indice }
                    ?.marco,
            tamano = tamano,
            ancho = caja.ancho,
            alto = caja.alto,
        )
    }

    val seleccion = estado.seleccion?.takeIf { it.pagina == indice }
    if (seleccion != null) {
        OverlaySeleccion(
            seleccion = seleccion,
            tamano = tamano,
            ancho = caja.ancho,
            alto = caja.alto,
            alMoverAsa = acciones.alMoverAsa,
        )
    }
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

/**
 * Una página, con el giro de la vista aplicado a la caja entera.
 *
 * El giro va en la caja de dentro y no en el bitmap por una razón que se paga cara si
 * se olvida: los overlays —campos, firma en el aire, resaltados, selección— se colocan
 * en puntos de página, y girar sólo la imagen los dejaría flotando donde el texto ya no
 * está. Girando la caja, todo lo de dentro sigue cuadrado con el papel y sólo el hueco
 * que ocupa en la pantalla cambia de forma.
 */
@Composable
private fun PaginaDelDocumento(
    indice: Int,
    mapa: ImageBitmap?,
    caja: CajaDePagina,
    giro: GiroDeVista,
    encima: @Composable () -> Unit = {},
) {
    Box(
        modifier =
            Modifier
                .width(caja.anchoVisible)
                .height(caja.altoVisible)
                .testTag(tagPagina(indice)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    // `requiredSize` y no `size`: girada, la caja del papel es más ancha
                    // que el hueco que ocupa, y dejarse recortar por él la aplastaría.
                    .requiredSize(width = caja.ancho, height = caja.alto)
                    .rotate(giro.grados)
                    // El papel es papel en los dos temas: el documento nunca se oscurece.
                    .background(ColoresPapel.papel, RoundedCornerShape(2.dp)),
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
}

/**
 * El «3 / 12» que flota sobre el documento.
 *
 * Se calla sola mientras no hay documento: el visor se compone antes de que el motor
 * conteste cuántas páginas tiene, y un «1 / 0» de medio segundo al abrir es de las
 * cosas que se quedan grabadas.
 */
@Composable
private fun PildoraPagina(
    estado: EstadoVisor,
    modifier: Modifier = Modifier,
) {
    if (estado.paginas <= 0) return

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
            text = "${estado.paginaActual + 1} / ${estado.paginas}",
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

private const val OPACIDAD_PILDORA = 0.92f

/** El zoom en reposo: la página tal como la deja el ajuste elegido, sin ampliar. */
private const val SIN_ZOOM = 1f

/** A donde lleva el doble toque: el doble del ajuste, que en A4 deja el texto legible. */
private const val EL_DOBLE = 2f

private const val ESPERA_TRAS_EL_GESTO_MS = 180L

/** Lo que se deja de página por encima del campo enfocado: un tercio de la altura. */
private const val MARGEN_SOBRE_EL_TECLADO = 0.33f

/**
 * A partir de este ancho se ofrecen dos páginas.
 *
 * Es el escalón que Material llama «medium», y aquí sale a unos 300 dp por página: por
 * debajo, una A4 partida en dos deja el cuerpo de texto por debajo de lo legible y la
 * doble página deja de ser una comodidad para ser un truco de escritorio mal traído.
 */
private val ANCHO_MINIMO_PARA_DOS = 600.dp

const val TAG_LISTA = "visor_lista"
const val TAG_PILDORA = "visor_pildora_pagina"

fun tagPagina(indice: Int): String = "visor_pagina_$indice"
