package com.marcmayol.dracpdf.ui.escaner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marcmayol.dracpdf.adaptadores.camara.RecorteDeHoja
import com.marcmayol.dracpdf.ui.iconos.BotonIconoLadon
import com.marcmayol.dracpdf.ui.iconos.IconosLadon
import com.marcmayol.dracpdf.ui.tema.MedidasLadon
import java.io.File

/**
 * El escáner: fotografiar hojas y montarlas en un PDF.
 *
 * **No es una conversión y por eso no vive dentro de «Convertir»**, aunque acabe en lo
 * mismo. Convertir es tomar algo que ya existe; escanear es fabricarlo, y por el camino
 * pide permiso de cámara, vista previa, disparo, recorte y una tanda de hojas que se
 * revisa antes de guardar nada.
 *
 * La tanda es la razón de que esto sea una pantalla y no un diálogo: un documento de
 * seis hojas son seis disparos seguidos, y preguntar dónde guardar después de cada uno
 * daría seis PDF de una página.
 */
@Composable
fun PantallaEscaner(
    modelo: EscanerViewModel,
    alGuardar: (List<File>) -> Unit,
    alSalir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val estado by modelo.estado.collectAsStateWithLifecycle()
    var hayPermiso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val pedirPermiso =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            hayPermiso = concedido
        }

    // Se pide al entrar y no antes: el permiso de cámara sólo tiene sentido explicado
    // por lo que se está haciendo, y aquí la pantalla ya dice que se va a escanear.
    LaunchedEffect(Unit) { if (!hayPermiso) pedirPermiso.launch(Manifest.permission.CAMERA) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
    ) {
        BarraDelEscaner(hojas = estado.hojas.size, alSalir = alSalir)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (hayPermiso) {
                VistaDeLaCamara(modelo = modelo)
            } else {
                SinPermiso(alPedir = { pedirPermiso.launch(Manifest.permission.CAMERA) })
            }
        }

        if (estado.hayHojas) {
            TiraDeHojas(
                hojas = estado.hojas,
                alDescartar = modelo::descartar,
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = MedidasLadon.margen, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    when {
                        estado.trabajando -> "Enderezando…"
                        estado.hayHojas -> "${estado.hojas.size} hojas"
                        else -> "Encuadra la hoja y dispara"
                    },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(TAG_ESCANER_ESTADO),
            )
            Button(
                onClick = { alGuardar(modelo.recortes()) },
                enabled = estado.hayHojas && !estado.trabajando,
                modifier = Modifier.testTag(TAG_ESCANER_GUARDAR),
            ) { Text("Guardar PDF") }
        }
    }
}

@Composable
private fun BarraDelEscaner(
    hojas: Int,
    alSalir: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotonIconoLadon(
            icono = IconosLadon.atras,
            descripcion = "Dejar de escanear",
            alPulsar = alSalir,
            modifier = Modifier.testTag(TAG_ESCANER_SALIR),
        )
        Text(
            text = if (hojas == 0) "Escanear" else "Escanear · $hojas",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * La vista previa de la cámara y el disparador.
 *
 * CameraX se engancha al ciclo de vida de la pantalla: al salir se suelta sola, que es
 * lo que evita dejar la cámara ocupada para el resto de aplicaciones.
 */
@Composable
private fun VistaDeLaCamara(modelo: EscanerViewModel) {
    val contexto = LocalContext.current
    val duenoDelCiclo = LocalLifecycleOwner.current
    val captura = remember { ImageCapture.Builder().build() }
    val vista = remember { PreviewView(contexto) }

    DisposableEffect(Unit) {
        val futuro = ProcessCameraProvider.getInstance(contexto)
        futuro.addListener({
            val proveedor = futuro.get()
            val previa = Preview.Builder().build().also { it.surfaceProvider = vista.surfaceProvider }
            runCatching {
                proveedor.unbindAll()
                proveedor.bindToLifecycle(duenoDelCiclo, CameraSelector.DEFAULT_BACK_CAMERA, previa, captura)
            }
        }, ContextCompat.getMainExecutor(contexto))

        onDispose {
            runCatching { ProcessCameraProvider.getInstance(contexto).get().unbindAll() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { vista }, modifier = Modifier.fillMaxSize().testTag(TAG_ESCANER_VISTA))

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .size(LADO_DISPARADOR)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        val destino = modelo.ficheroParaLaFoto()
                        captura.takePicture(
                            ImageCapture.OutputFileOptions.Builder(destino).build(),
                            ContextCompat.getMainExecutor(contexto),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(resultado: ImageCapture.OutputFileResults) {
                                    modelo.capturada(destino)
                                }

                                override fun onError(fallo: ImageCaptureException) = Unit
                            },
                        )
                    }.testTag(TAG_ESCANER_DISPARAR),
        )
    }
}

@Composable
private fun SinPermiso(alPedir: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(MedidasLadon.margen),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text =
                "Para escanear hace falta la cámara. No se guarda ninguna foto fuera de este " +
                    "escaneo: las hojas se borran en cuanto se cierra la pantalla.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = alPedir,
            modifier = Modifier.padding(top = 16.dp).testTag(TAG_ESCANER_PERMISO),
        ) { Text("Dar permiso de cámara") }
    }
}

/** Las hojas capturadas, en fila y en orden: es el documento que se está montando. */
@Composable
private fun TiraDeHojas(
    hojas: List<HojaEscaneada>,
    alDescartar: (Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(ALTO_TIRA).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(hojas, key = { _, hoja -> hoja.recorte.absolutePath }) { posicion, hoja ->
            Box(modifier = Modifier.width(ANCHO_MINIATURA).testTag(tagHojaEscaneada(posicion))) {
                val vistaPrevia =
                    remember(hoja.recorte.absolutePath, hoja.esquinas) { RecorteDeHoja.fotoDe(hoja.recorte) }
                Image(
                    bitmap = vistaPrevia.asImageBitmap(),
                    contentDescription = "Hoja ${posicion + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(Color.White),
                )
                BotonIconoLadon(
                    icono = IconosLadon.cerrar,
                    descripcion = "Descartar esta hoja",
                    alPulsar = { alDescartar(posicion) },
                    modifier = Modifier.align(Alignment.TopEnd).testTag(tagDescartarHoja(posicion)),
                )
            }
        }
    }
}

private val LADO_DISPARADOR = 72.dp
private val ALTO_TIRA = 120.dp
private val ANCHO_MINIATURA = 88.dp

const val TAG_ESCANER_VISTA = "escaner_vista"
const val TAG_ESCANER_DISPARAR = "escaner_disparar"
const val TAG_ESCANER_GUARDAR = "escaner_guardar"
const val TAG_ESCANER_SALIR = "escaner_salir"
const val TAG_ESCANER_ESTADO = "escaner_estado"
const val TAG_ESCANER_PERMISO = "escaner_permiso"

fun tagHojaEscaneada(posicion: Int): String = "escaner_hoja_$posicion"

fun tagDescartarHoja(posicion: Int): String = "escaner_descartar_$posicion"
