package com.marcmayol.dracpdf

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marcmayol.dracpdf.adaptadores.saf.AjustesDelSistema
import com.marcmayol.dracpdf.adaptadores.saf.OrigenesDelSistema
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.DocumentoReciente
import com.marcmayol.dracpdf.dominio.puertos.TipoDeEntrada
import com.marcmayol.dracpdf.dominio.registro.EstadoDocumento
import com.marcmayol.dracpdf.ui.ajustes.HojaAjustes
import com.marcmayol.dracpdf.ui.compartir.Compartir
import com.marcmayol.dracpdf.ui.documentos.DocumentoEnLista
import com.marcmayol.dracpdf.ui.documentos.HojaDocumentos
import com.marcmayol.dracpdf.ui.escaner.EscanerViewModel
import com.marcmayol.dracpdf.ui.escaner.PantallaEscaner
import com.marcmayol.dracpdf.ui.firmas.FirmasViewModel
import com.marcmayol.dracpdf.ui.herramientas.CasosDeHerramientas
import com.marcmayol.dracpdf.ui.herramientas.Herramienta
import com.marcmayol.dracpdf.ui.herramientas.HerramientasViewModel
import com.marcmayol.dracpdf.ui.inicio.HojaContrasena
import com.marcmayol.dracpdf.ui.inicio.PantallaInicio
import com.marcmayol.dracpdf.ui.inicio.RecienteEnLista
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import com.marcmayol.dracpdf.ui.tema.TemaViewModel
import com.marcmayol.dracpdf.ui.visor.PantallaVisor
import com.marcmayol.dracpdf.ui.visor.VisorViewModel

/**
 * Única actividad de la aplicación.
 *
 * Edge-to-edge siempre activo y barras del sistema transparentes, como manda el
 * diseño: el modo inmersivo del visor no es más que ocultarlas, así que no puede
 * haber una barra opaca que se quede a medias.
 */
class MainActivity : ComponentActivity() {
    private val grafo: Grafo get() = (application as AplicacionDracPDF).grafo

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val temaModelo: TemaViewModel = viewModel(factory = fabricaDe(grafo))
            val preferencia by temaModelo.preferencia.collectAsStateWithLifecycle()

            TemaDracPDF(preferencia = preferencia) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AplicacionDracPDFUi(
                        grafo = grafo,
                        tema = temaModelo,
                        // Sólo en la primera creación: al girar la pantalla el intent
                        // sigue ahí, y sin esto el documento se reabriría desde cero
                        // perdiendo la página por la que iba.
                        intentInicial = if (savedInstanceState == null) intent else null,
                    )
                }
            }
        }
    }

    /**
     * Llega un documento con la aplicación ya abierta (otro `VIEW`, otro envío). Se
     * guarda para que la interfaz lo recoja.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentPendiente = intent
    }

    companion object {
        /**
         * El intent que aún no se ha atendido. Es la forma más simple de que
         * `onNewIntent`, que ocurre fuera de la composición, llegue hasta ella.
         */
        @Volatile
        var intentPendiente: Intent? = null
    }
}

/**
 * La pantalla raíz: decide qué se ve —inicio, contraseña, documento o error— y monta
 * encima las hojas que no dependen de eso.
 *
 * Es la función más ramificada de la aplicación y no hay dónde repartirla sin empeorar
 * las cosas: los flujos de herramientas ya viven en su fichero, las hojas del visor en
 * el suyo y los diálogos en el de al lado. Lo que queda aquí es exactamente la decisión
 * de qué pantalla toca, y esa decisión es una y está en un sitio.
 */
@Suppress("CyclomaticComplexMethod")
@Composable
private fun AplicacionDracPDFUi(
    grafo: Grafo,
    tema: TemaViewModel,
    intentInicial: Intent?,
) {
    val contexto = LocalContext.current
    val resolver = remember { contexto.contentResolver }
    val preferenciaTema by tema.preferencia.collectAsStateWithLifecycle()
    var temaAbierto by remember { mutableStateOf(false) }
    var ajustesAbiertos by remember { mutableStateOf(false) }
    var escaneando by remember { mutableStateOf(false) }

    val appModelo: AppViewModel = viewModel(factory = fabricaDe(grafo))
    val visorModelo: VisorViewModel = viewModel(factory = fabricaDe(grafo))
    val firmasModelo: FirmasViewModel = viewModel(factory = fabricaDe(grafo))
    val estado by appModelo.estado.collectAsStateWithLifecycle()
    val abiertos by appModelo.abiertos.collectAsStateWithLifecycle()
    val miniaturasDocs by appModelo.miniaturas.collectAsStateWithLifecycle()

    // Las páginas del documento que se está viendo, en pequeño: las comparten la hoja
    // de índice y la de organizar, y las dibuja el mismo modelo para no rasterizar dos
    // veces lo mismo.
    val miniaturasDelVisor by visorModelo.miniaturas.collectAsStateWithLifecycle()
    val recientes by appModelo.recientes.collectAsStateWithLifecycle()
    val efimeros by appModelo.efimeros.collectAsStateWithLifecycle()

    // La lista se trae al entrar y cada vez que se vuelve al inicio: es lo que hace que
    // un documento cerrado hace un segundo ya aparezca ahí.
    LaunchedEffect(Unit) { appModelo.refrescarRecientes() }
    var documentosAbiertosVisible by remember { mutableStateOf(false) }

    val selector =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                // Se pide el permiso permanente antes de abrir: si el proveedor lo
                // concede, mañana el documento seguirá abriéndose desde recientes. Y si
                // no lo concede, el reciente se apunta sabiendo que puede no abrir.
                val permanente = OrigenesDelSistema.recordarPermiso(resolver, it)
                appModelo.abrir(OrigenesDelSistema.de(resolver, it), permisoPersistido = permanente)
            }
        }

    val herramientasModelo: HerramientasViewModel = viewModel(factory = fabricaDe(grafo))
    val trabajo by herramientasModelo.estado.collectAsStateWithLifecycle()

    /**
     * La herramienta elegida espera destino. Se guarda aquí porque el selector del
     * sistema es una ida y vuelta: cuando el usuario elige dónde guardar, ya no queda
     * nada en pantalla que recuerde qué había pedido.
     */
    var esperandoDestino by remember { mutableStateOf<Herramienta?>(null) }

    /** Lo que cada flujo va recogiendo antes de pedir el destino. */
    var pedido by remember { mutableStateOf(LoPedido()) }

    /** Qué diálogo hay que enseñar ahora mismo, si hay alguno. */
    var preguntando by remember { mutableStateOf<Herramienta?>(null) }

    val destinoParaGuardar =
        recordarSelectorDeDestino(
            herramientaPendiente = { esperandoDestino },
            alResolver = { herramienta, destino ->
                esperandoDestino = null
                val id = (estado as? EstadoApp.Viendo)?.id ?: return@recordarSelectorDeDestino
                ejecutarConDestino(
                    herramienta = herramienta,
                    modelo = herramientasModelo,
                    origen = grafo.repositorio.origenDe(id),
                    destino = destino,
                    pedido = pedido,
                )
            },
        )

    // Las imágenes no van a un fichero sino a una carpeta: son una por página, y
    // pedirlas de una en una sería insufrible en un documento de veinte.
    val carpetaParaImagenes =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { arbol: Uri? ->
            val id = (estado as? EstadoApp.Viendo)?.id
            if (arbol != null && id != null) {
                convertirEnLaCarpeta(
                    modelo = herramientasModelo,
                    documento =
                        DocumentoEnCurso(
                            id = id,
                            origen = grafo.repositorio.origenDe(id),
                            nombreBase = grafo.nombreDelDocumento(id).substringBeforeLast('.', "documento"),
                        ),
                    carpeta = OrigenesDelSistema.carpetaDe(resolver, arbol),
                    pedido = pedido,
                )
            }
        }

    val selectorParaConvertir = recordarCreacionDePdf(resolver, herramientasModelo)

    val destinoDeLaCopia =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TIPO_PDF)) { uri: Uri? ->
            val id = (estado as? EstadoApp.Viendo)?.id
            if (uri != null && id != null) {
                appModelo.guardarCopia(id, OrigenDocumento.Externo(uri.toString(), nombreDeUri(uri)))
            }
        }

    val selectorParaUnir =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
            if (uris.size >= 2) {
                val elegidos = uris.map { OrigenDocumento.Externo(it.toString(), nombreDeUri(it)) }
                pedido = pedido.copy(paraUnir = elegidos)
                herramientasModelo.revisar(elegidos)
            }
        }

    // Un documento que llega de fuera: del explorador de archivos, del correo, o del
    // menú de compartir de cualquier aplicación.
    LaunchedEffect(intentInicial) { abrirLoQueTrae(intentInicial, resolver, appModelo) }
    LaunchedEffect(Unit) {
        MainActivity.intentPendiente?.let { pendiente ->
            MainActivity.intentPendiente = null
            abrirLoQueTrae(pendiente, resolver, appModelo)
        }
    }

    val documentoAbierto = (estado as? EstadoApp.Viendo)?.id
    val nombreDelAbierto = grafo.nombreDelDocumento(documentoAbierto)

    val pedirDestino: (Herramienta) -> Unit = { herramienta ->
        esperandoDestino = herramienta
        destinoParaGuardar.launch(nombreSugerido(herramienta, nombreDelAbierto, pedido))
    }

    FlujosDeHerramienta(
        preguntando = preguntando,
        abierto =
            DatosDelAbierto(
                paginas = grafo.paginasDelDocumento(documentoAbierto),
                miniaturas = miniaturasDelVisor,
                alPedirMiniatura = visorModelo::pedirMiniatura,
            ),
        avisos = trabajo.avisos,
        acciones =
            AccionesDeFlujo(
                alDejarDePreguntar = { preguntando = null },
                alPedirDestino = pedirDestino,
                alPedirCarpeta = { carpetaParaImagenes.launch(null) },
                alRecoger = { recogido -> pedido = recogido(pedido) },
                alOlvidarAvisos = {
                    herramientasModelo.descartarResultado()
                    pedido = pedido.copy(paraUnir = emptyList())
                },
                alSeguirTrasAvisos = herramientasModelo::descartarResultado,
            ),
    )

    if (ajustesAbiertos) {
        HojaAjustes(
            // Ninguna aplicación puede hacerse predeterminada a sí misma: lo más lejos
            // que se puede llegar es dejar al usuario donde el sistema sí lo decide.
            alAbrirAjustesDelSistema = { AjustesDelSistema.abrirDeEstaAplicacion(contexto) },
            alCerrar = { ajustesAbiertos = false },
            esElPredeterminado = AjustesDelSistema.somosElLectorPorDefecto(contexto),
        )
    }

    HojasSueltas(
        trabajo = trabajo,
        temaAbierto = temaAbierto,
        preferenciaTema = preferenciaTema,
        alCancelarTrabajo = herramientasModelo::cancelar,
        alCerrarTrabajo = herramientasModelo::descartarResultado,
        alElegirTema = tema::elegir,
        alCerrarTema = { temaAbierto = false },
    )

    if (escaneando) {
        val escanerModelo: EscanerViewModel = viewModel(factory = fabricaDe(grafo))
        PantallaEscaner(
            modelo = escanerModelo,
            alGuardar = { recortes ->
                escaneando = false
                herramientasModelo.crearPdfDesdeEscaneo(recortes, contexto.cacheDir)
                escanerModelo.terminar()
            },
            alSalir = {
                escaneando = false
                escanerModelo.terminar()
            },
        )
        return
    }

    when (val actual = estado) {
        is EstadoApp.Inicio ->
            PantallaInicio(
                alAbrirPdf = { selector.launch(arrayOf(TIPO_PDF)) },
                alAbrirTema = { temaAbierto = true },
                alAbrirAjustes = { ajustesAbiertos = true },
                alCrearPdf = { selectorParaConvertir.launch(TipoDeEntrada.MIMES_ACEPTADOS.toTypedArray()) },
                alEscanear = { escaneando = true },
                abiertos =
                    abiertos.map {
                        it.aDocumentoEnLista(
                            activo = false,
                            miniatura = miniaturasDocs[it.id.valor],
                        )
                    },
                alElegirAbierto = { id -> appModelo.cambiarA(IdDocumento(id)) },
                alCerrarAbierto = { id -> appModelo.cerrar(IdDocumento(id)) },
                recientes = recientes.map { it.aRecienteEnLista() },
                alElegirReciente = { identificador ->
                    recientes.firstOrNull { it.origen.identificador == identificador }?.let {
                        appModelo.abrir(it.origen, permisoPersistido = it.permisoPersistido)
                    }
                },
                alOlvidarReciente = appModelo::olvidarReciente,
            )

        is EstadoApp.PidiendoContrasena -> {
            PantallaInicio(
                alAbrirPdf = { selector.launch(arrayOf(TIPO_PDF)) },
                alAbrirTema = { temaAbierto = true },
            )
            HojaContrasena(
                nombreDocumento = nombreDe(actual.origen),
                huboError = actual.fallo,
                alAceptar = { contrasena -> appModelo.abrir(actual.origen, contrasena) },
                alCancelar = appModelo::cancelarContrasena,
            )
        }

        is EstadoApp.Viendo -> {
            LaunchedEffect(actual.id) { visorModelo.mostrar(actual.id) }
            PantallaVisor(
                modelo = visorModelo,
                alSalir = appModelo::volverAlInicio,
                documentosAbiertos = abiertos.size,
                alAbrirDocumentos = { documentosAbiertosVisible = true },
                alAbrirOtro = { selector.launch(arrayOf(TIPO_PDF)) },
                alElegirHerramienta = { herramienta ->
                    arrancar(
                        herramienta = herramienta,
                        alPedirDestino = pedirDestino,
                        alElegirVarios = { selectorParaUnir.launch(arrayOf(TIPO_PDF)) },
                        alPreguntar = { preguntando = it },
                    )
                },
                firmas = firmasModelo,
                alImprimir = { imprimir(contexto, grafo, actual.id, nombreDelAbierto) },
                alCompartirDocumento = {
                    Compartir.documento(contexto, grafo.repositorio.origenDe(actual.id), nombreDelAbierto)
                },
                alGuardarCopia = {
                    destinoDeLaCopia.launch(nombreDeLaCopia(nombreDelAbierto, actual.id.valor in efimeros))
                },
            )

            if (documentosAbiertosVisible) {
                HojaDocumentos(
                    documentos =
                        abiertos.map { it.aDocumentoEnLista(it.id == actual.id, miniaturasDocs[it.id.valor]) },
                    alPedirMiniatura = appModelo::pedirMiniatura,
                    alElegir = { id ->
                        appModelo.cambiarA(IdDocumento(id))
                        documentosAbiertosVisible = false
                    },
                    alCerrarDocumento = { id -> appModelo.cerrar(IdDocumento(id)) },
                    alAbrirOtro = {
                        documentosAbiertosVisible = false
                        selector.launch(arrayOf(TIPO_PDF))
                    },
                    alCerrarTodos = {
                        documentosAbiertosVisible = false
                        appModelo.cerrarTodos()
                    },
                    alCerrar = { documentosAbiertosVisible = false },
                )
            }
        }

        is EstadoApp.NoSePudoAbrir ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        // No tiene barras propias que aparten el contenido, así que se
                        // aparta de todas de una vez.
                        .safeDrawingPadding()
                        .padding(32.dp)
                        .testTag(TAG_ERROR_APERTURA),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${actual.nombre}\n\n${actual.motivo}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
    }
}

/**
 * Cuántas páginas y cómo se llama el documento que hay delante.
 *
 * Con la aplicación en el inicio no hay ninguno, y entonces se contesta con lo que no
 * estorba: una página y un nombre vacío. Los diálogos se construyen antes de saber si
 * habrá documento, y un valor imposible aquí sería un fallo allí.
 */
internal fun Grafo.paginasDelDocumento(id: IdDocumento?): Int =
    id?.let {
        registro
            .estado(it)
            .documento.paginas
    } ?: 1

internal fun Grafo.nombreDelDocumento(id: IdDocumento?): String =
    id
        ?.let {
            registro
                .estado(it)
                .documento.nombre
        }.orEmpty()

/**
 * Traduce un reciente del dominio a lo que enseña el inicio.
 *
 * Lo de «hace un rato» se calcula aquí y no en el composable: es una cuenta que
 * depende del reloj, y dentro de la composición se rehace en cada recomposición sin
 * que nadie se lo haya pedido.
 */
private fun DocumentoReciente.aRecienteEnLista(): RecienteEnLista =
    RecienteEnLista(
        identificador = origen.identificador,
        nombre = nombre,
        cuando = haceCuanto(visto),
        // La página sólo se enseña si se dejó empezado: decir «pág. 1» de todo lo que
        // se ha abierto alguna vez es ruido.
        porDonde = if (pagina > 0) "pág. ${pagina + 1}" else null,
        puedeQueNoAbra = !permisoPersistido,
    )

/**
 * Abre el documento que trae un intent, sabiendo con qué permiso llega.
 *
 * El permiso se pregunta **antes** de intentar quedárselo: sólo el selector de
 * documentos lo concede para siempre, y pedirlo sobre un envío de WhatsApp lanza
 * `SecurityException`. Lo que llega sin permiso duradero se abre igual, pero la
 * aplicación sabe que es prestado y podrá ofrecer guardar una copia en vez de dejar
 * creer que ese documento seguirá ahí mañana.
 */
private fun abrirLoQueTrae(
    intent: Intent?,
    resolver: ContentResolver,
    modelo: AppViewModel,
) {
    val origen = OrigenesDelSistema.delIntent(resolver, intent) ?: return
    val persistible =
        OrigenesDelSistema.permisoPersistibleDe(intent) &&
            OrigenesDelSistema.recordarPermiso(resolver, Uri.parse(origen.identificador))
    modelo.abrir(origen, permisoPersistido = persistible)
}

private fun haceCuanto(cuando: Long): String {
    val minutos = (System.currentTimeMillis() - cuando) / MILIS_POR_MINUTO
    return when {
        minutos < MINUTOS_DE_UN_RATO -> "hace un momento"
        minutos < MINUTOS_POR_HORA -> "hace $minutos min"
        minutos < MINUTOS_POR_DIA -> "hace ${minutos / MINUTOS_POR_HORA} h"
        minutos < MINUTOS_POR_SEMANA -> "hace ${minutos / MINUTOS_POR_DIA} días"
        else -> "hace ${minutos / MINUTOS_POR_SEMANA} semanas"
    }
}

/** Traduce lo que sabe el registro a lo que necesita la lista de la interfaz. */
private fun EstadoDocumento.aDocumentoEnLista(
    activo: Boolean,
    miniatura: androidx.compose.ui.graphics.ImageBitmap? = null,
) = DocumentoEnLista(
    id = id.valor,
    nombre = documento.nombre,
    paginaActual = paginaActual,
    paginas = documento.paginas,
    abiertoEn = abiertoEn,
    activo = activo,
    miniatura = miniatura,
)

/**
 * El selector de «dónde guardar» del sistema, con la herramienta que lo pidió.
 *
 * Vive aparte porque es una ida y vuelta fuera de la aplicación: cuando el usuario
 * vuelve, en pantalla no queda nada que recuerde qué había pedido, así que la
 * herramienta se consulta al volver y no se captura al salir.
 */
@Composable
private fun recordarSelectorDeDestino(
    herramientaPendiente: () -> Herramienta?,
    alResolver: (Herramienta, OrigenDocumento) -> Unit,
) = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TIPO_PDF)) { uri: Uri? ->
    val herramienta = herramientaPendiente()
    if (uri != null && herramienta != null) {
        alResolver(herramienta, OrigenDocumento.Externo(uri.toString(), nombreDeUri(uri)))
    }
}

/**
 * Fábrica de modelos. Con un grafo montado a mano hace falta decirle a Compose cómo
 * construir cada uno; son dos, y así ninguno se crea con dependencias de mentira.
 */
private fun fabricaDe(grafo: Grafo) =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(clase: Class<T>): T =
            when {
                clase.isAssignableFrom(AppViewModel::class.java) ->
                    AppViewModel(
                        grafo.abrirDocumento,
                        grafo.cerrarDocumento,
                        grafo.registro,
                        grafo.renderizarPagina,
                        grafo.firmarDocumento,
                        grafo.recordarDocumentos,
                        grafo.repositorio,
                    ) as T

                clase.isAssignableFrom(FirmasViewModel::class.java) ->
                    FirmasViewModel(grafo.almacenFirmas) as T

                clase.isAssignableFrom(EscanerViewModel::class.java) ->
                    EscanerViewModel(grafo.carpetaDeEscaneos) as T

                clase.isAssignableFrom(TemaViewModel::class.java) ->
                    TemaViewModel(grafo.ajustesDeInterfaz, grafo.temaInicial) as T

                clase.isAssignableFrom(HerramientasViewModel::class.java) ->
                    HerramientasViewModel(
                        CasosDeHerramientas(
                            unir = grafo.unirDocumentos,
                            organizar = grafo.organizarPaginas,
                            dividir = grafo.dividirDocumento,
                            proteger = grafo.protegerDocumento,
                            desproteger = grafo.desprotegerDocumento,
                            comprimir = grafo.comprimirDocumento,
                            convertir = grafo.convertirDocumento,
                            convertirEstructura = grafo.convertirEstructura,
                            crearPdf = grafo.convertirAPdf,
                        ),
                        grafo.revisarAntesDeOperar,
                    ) as T

                clase.isAssignableFrom(VisorViewModel::class.java) ->
                    VisorViewModel(
                        grafo.casosDelVisor,
                        grafo.registro,
                        grafo.cachePaginas,
                        // Sin esto la vista elegida funciona pero no sobrevive a cerrar
                        // la aplicación, que es justo lo que la fase promete.
                        ajustes = grafo.ajustesDeInterfaz,
                    ) as T

                else -> error("No sé construir ${clase.name}")
            }
    }

internal const val TIPO_PDF = "application/pdf"

private const val MILIS_POR_MINUTO = 60_000L
private const val MINUTOS_DE_UN_RATO = 2
private const val MINUTOS_POR_HORA = 60
private const val MINUTOS_POR_DIA = 60 * 24
private const val MINUTOS_POR_SEMANA = 60 * 24 * 7

const val TAG_ERROR_APERTURA = "apertura_error"
