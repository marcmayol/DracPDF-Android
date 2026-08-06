package com.marcmayol.dracpdf

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marcmayol.dracpdf.adaptadores.saf.OrigenesDelSistema
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.registro.EstadoDocumento
import com.marcmayol.dracpdf.ui.documentos.DocumentoEnLista
import com.marcmayol.dracpdf.ui.documentos.HojaDocumentos
import com.marcmayol.dracpdf.ui.firmas.FirmasViewModel
import com.marcmayol.dracpdf.ui.herramientas.CasosDeHerramientas
import com.marcmayol.dracpdf.ui.herramientas.Herramienta
import com.marcmayol.dracpdf.ui.herramientas.HerramientasViewModel
import com.marcmayol.dracpdf.ui.herramientas.HojaProgreso
import com.marcmayol.dracpdf.ui.inicio.HojaContrasena
import com.marcmayol.dracpdf.ui.inicio.PantallaInicio
import com.marcmayol.dracpdf.ui.tema.HojaTema
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

    val appModelo: AppViewModel = viewModel(factory = fabricaDe(grafo))
    val visorModelo: VisorViewModel = viewModel(factory = fabricaDe(grafo))
    val firmasModelo: FirmasViewModel = viewModel(factory = fabricaDe(grafo))
    val estado by appModelo.estado.collectAsStateWithLifecycle()
    val abiertos by appModelo.abiertos.collectAsStateWithLifecycle()
    val miniaturasDocs by appModelo.miniaturas.collectAsStateWithLifecycle()
    var documentosAbiertosVisible by remember { mutableStateOf(false) }

    val selector =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                // Se pide el permiso permanente antes de abrir: si el proveedor lo
                // concede, mañana el documento seguirá abriéndose desde recientes.
                OrigenesDelSistema.recordarPermiso(resolver, it)
                appModelo.abrir(OrigenesDelSistema.de(resolver, it))
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

    val destinoParaGuardar =
        recordarSelectorDeDestino(
            herramientaPendiente = { esperandoDestino },
            alResolver = { herramienta, destino ->
                esperandoDestino = null
                val id = (estado as? EstadoApp.Viendo)?.id ?: return@recordarSelectorDeDestino
                val origen = grafo.repositorio.origenDe(id)
                when (herramienta) {
                    Herramienta.COMPRIMIR -> herramientasModelo.comprimir(origen, destino)
                    Herramienta.CONVERTIR -> herramientasModelo.convertirATexto(origen, destino)
                    else -> Unit
                }
            },
        )

    // Un documento que llega de fuera: del explorador de archivos, del correo, o del
    // menú de compartir de cualquier aplicación.
    LaunchedEffect(intentInicial) {
        OrigenesDelSistema.delIntent(resolver, intentInicial)?.let(appModelo::abrir)
    }
    LaunchedEffect(Unit) {
        MainActivity.intentPendiente?.let { pendiente ->
            MainActivity.intentPendiente = null
            OrigenesDelSistema.delIntent(resolver, pendiente)?.let(appModelo::abrir)
        }
    }

    if (trabajo.herramienta != null) {
        HojaProgreso(
            estado = trabajo,
            alCancelar = herramientasModelo::cancelar,
            alCerrar = herramientasModelo::descartarResultado,
        )
    }

    if (temaAbierto) {
        HojaTema(
            elegida = preferenciaTema,
            alElegir = tema::elegir,
            alCerrar = { temaAbierto = false },
        )
    }

    when (val actual = estado) {
        is EstadoApp.Inicio ->
            PantallaInicio(
                alAbrirPdf = { selector.launch(arrayOf(TIPO_PDF)) },
                alAbrirTema = { temaAbierto = true },
                abiertos =
                    abiertos.map {
                        it.aDocumentoEnLista(
                            activo = false,
                            miniatura = miniaturasDocs[it.id.valor],
                        )
                    },
                alElegirAbierto = { id -> appModelo.cambiarA(IdDocumento(id)) },
                alCerrarAbierto = { id -> appModelo.cerrar(IdDocumento(id)) },
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
                    esperandoDestino = herramienta
                    destinoParaGuardar.launch(
                        nombreSugerido(
                            herramienta,
                            grafo.registro
                                .estado(actual.id)
                                .documento.nombre,
                        ),
                    )
                },
                firmas = firmasModelo,
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
 * Qué nombre proponer al guardar. Se propone y no se impone: el selector del sistema
 * deja cambiarlo, y con dos documentos parecidos abiertos el nombre de partida es lo
 * único que distingue un resultado de otro.
 */
private fun nombreSugerido(
    herramienta: Herramienta,
    nombreDelDocumento: String,
): String {
    val base = nombreDelDocumento.substringBeforeLast('.', nombreDelDocumento)
    return when (herramienta) {
        Herramienta.COMPRIMIR -> "$base-comprimido.pdf"
        Herramienta.CONVERTIR -> "$base.txt"
        Herramienta.PROTEGER -> "$base-protegido.pdf"
        Herramienta.DIVIDIR -> "$base-parte1.pdf"
        Herramienta.UNIR -> "documentos-unidos.pdf"
        else -> "$base.pdf"
    }
}

/** El nombre que el proveedor le ha puesto de verdad al fichero recién creado. */
private fun nombreDeUri(uri: Uri): String = uri.lastPathSegment?.substringAfterLast('/') ?: "resultado.pdf"

private fun nombreDe(origen: OrigenDocumento): String =
    when (origen) {
        is OrigenDocumento.Externo -> origen.nombre
        is OrigenDocumento.Privado -> origen.nombre
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
                    ) as T

                clase.isAssignableFrom(FirmasViewModel::class.java) ->
                    FirmasViewModel(grafo.almacenFirmas) as T

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
                        ),
                        grafo.revisarAntesDeOperar,
                    ) as T

                clase.isAssignableFrom(VisorViewModel::class.java) ->
                    VisorViewModel(
                        grafo.casosDelVisor,
                        grafo.registro,
                        grafo.cachePaginas,
                    ) as T

                else -> error("No sé construir ${clase.name}")
            }
    }

private const val TIPO_PDF = "application/pdf"

const val TAG_ERROR_APERTURA = "apertura_error"
