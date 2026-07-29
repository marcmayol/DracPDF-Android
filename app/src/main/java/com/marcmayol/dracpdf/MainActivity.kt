package com.marcmayol.dracpdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.marcmayol.dracpdf.ui.inicio.HojaContrasena
import com.marcmayol.dracpdf.ui.inicio.PantallaInicio
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
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
            TemaDracPDF {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AplicacionDracPDFUi(
                        grafo = grafo,
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
    intentInicial: Intent?,
) {
    val contexto = LocalContext.current
    val resolver = remember { contexto.contentResolver }

    val appModelo: AppViewModel = viewModel(factory = fabricaDe(grafo))
    val visorModelo: VisorViewModel = viewModel(factory = fabricaDe(grafo))
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

    when (val actual = estado) {
        is EstadoApp.Inicio ->
            PantallaInicio(
                alAbrirPdf = { selector.launch(arrayOf(TIPO_PDF)) },
                temaOscuro = isSystemInDarkTheme(),
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
                temaOscuro = isSystemInDarkTheme(),
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
                modifier = Modifier.fillMaxSize().padding(32.dp).testTag(TAG_ERROR_APERTURA),
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
