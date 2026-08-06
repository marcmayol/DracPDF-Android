package com.marcmayol.dracpdf.ui.documentos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.ui.iconos.BotonIconoLadon
import com.marcmayol.dracpdf.ui.iconos.EstadoIcono
import com.marcmayol.dracpdf.ui.iconos.IconosLadon
import com.marcmayol.dracpdf.ui.tema.ColoresPapel
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * Los documentos abiertos a la vez.
 *
 * Hoja inferior y no pestañas, por decisión del diseño: unas pestañas bajo la barra
 * superior serían chrome permanente, y la regla de esta aplicación es que el chrome
 * es un invitado que se va solo.
 *
 * La misma lista se enseña en dos sitios —aquí y en la sección «Abiertos» de la
 * pantalla de inicio— y por eso es un solo componente: es la misma lista en dos
 * lugares, no dos diseños que se parecen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDocumentos(
    documentos: List<DocumentoEnLista>,
    alPedirMiniatura: (String) -> Unit,
    alElegir: (String) -> Unit,
    alCerrarDocumento: (String) -> Unit,
    alAbrirOtro: () -> Unit,
    alCerrarTodos: () -> Unit,
    alCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    // Abrirla ya entera en vez de a media altura. Es un booleano y no el `SheetState`
    // de Material para no obligar a cada llamante a aceptar una API experimental que no
    // le hace falta. Lo usa el test de viewport, que necesita ver el pie sin arrastrar
    // la hoja a mano.
    expandidaDelTodo: Boolean = false,
) {
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = expandidaDelTodo)
    LaunchedEffect(documentos.size) {
        documentos.filter { it.miniatura == null }.forEach { alPedirMiniatura(it.id) }
    }

    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = estadoHoja,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.testTag(TAG_HOJA_DOCUMENTOS),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Documentos abiertos",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${documentos.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        // La lista cede el sitio al pie y se desplaza dentro de lo que le queda.
        // Antes crecía sin freno: con ocho documentos abiertos, «Abrir otro PDF» y
        // «Cerrar todos» quedaban fuera de la pantalla y no había manera de llegar a
        // ellos, porque tampoco había scroll que los trajera.
        ListaDocumentos(
            documentos = documentos,
            alElegir = alElegir,
            alCerrarDocumento = alCerrarDocumento,
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .testTag(TAG_LISTA_DOCUMENTOS),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // Los 24 dp de aire de la maqueta se miden desde donde acaba la
                    // pantalla, no desde donde empiezan los botones del sistema.
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 24.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                onClick = alAbrirOtro,
                modifier = Modifier.heightIn(min = MedidasLadon.areaTactil).testTag(TAG_ABRIR_OTRO),
            ) {
                Text("Abrir otro PDF", color = MaterialTheme.colorScheme.primary)
            }
            TextButton(
                onClick = alCerrarTodos,
                enabled = documentos.isNotEmpty(),
                modifier = Modifier.heightIn(min = MedidasLadon.areaTactil).testTag(TAG_CERRAR_TODOS),
            ) {
                Text("Cerrar todos", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * La lista de documentos abiertos.
 *
 * Vive aparte de la hoja porque la pantalla de inicio enseña exactamente la misma
 * lista en su sección «Abiertos». Compartir el componente es lo que garantiza que
 * sean la misma lista en dos sitios, y no dos listas que hoy se parecen y dentro de
 * tres meses ya no.
 */
@Composable
fun ListaDocumentos(
    documentos: List<DocumentoEnLista>,
    alElegir: (String) -> Unit,
    alCerrarDocumento: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        documentos.forEach { documento ->
            FilaDocumento(
                documento = documento,
                alElegir = { alElegir(documento.id) },
                alCerrar = { alCerrarDocumento(documento.id) },
            )
        }
    }
}

/**
 * Una fila de la lista: miniatura, nombre, por dónde iba y cuándo se abrió, y la ✕.
 *
 * El activo se distingue por el fondo y por el borde de acento de su miniatura, no
 * sólo por el color del texto: quien no distinga bien los colores tiene que poder
 * saber cuál está mirando.
 */
@Composable
private fun FilaDocumento(
    documento: DocumentoEnLista,
    alElegir: () -> Unit,
    alCerrar: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ALTO_FILA)
                .background(
                    if (documento.activo) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                ).clickable(onClick = alElegir)
                .padding(horizontal = MedidasLadon.margen, vertical = 8.dp)
                .testTag(tagDocumento(documento.id)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(ANCHO_MINIATURA, ALTO_MINIATURA)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ColoresPapel.papel)
                    .then(
                        if (documento.activo) {
                            Modifier.border(
                                MedidasLadon.contornoSeleccion,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(3.dp),
                            )
                        } else {
                            Modifier
                        },
                    ),
        ) {
            documento.miniatura?.let { mapa ->
                Image(
                    bitmap = mapa,
                    contentDescription = null,
                    modifier = Modifier.size(ANCHO_MINIATURA, ALTO_MINIATURA),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = documento.nombre,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (documento.activo) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = descripcionDe(documento),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        BotonIconoLadon(
            icono = IconosLadon.cerrar,
            descripcion = "Cerrar ${documento.nombre}",
            alPulsar = alCerrar,
            estado = EstadoIcono.APAGADO,
            modifier = Modifier.testTag(tagCerrarDocumento(documento.id)),
        )
    }
}

/** «pág. 3 de 12 · hace 2 min». */
private fun descripcionDe(documento: DocumentoEnLista): String =
    "pág. ${documento.paginaActual + 1} de ${documento.paginas} · ${haceCuanto(documento.abiertoEn)}"

private fun haceCuanto(instante: Long): String {
    val minutos = (System.currentTimeMillis() - instante) / MILIS_POR_MINUTO
    return when {
        minutos < 1 -> "ahora mismo"
        minutos < MINUTOS_POR_HORA -> "hace $minutos min"
        minutos < MINUTOS_POR_DIA -> "hace ${minutos / MINUTOS_POR_HORA} h"
        else -> "hace ${minutos / MINUTOS_POR_DIA} d"
    }
}

private val ALTO_FILA = 60.dp
private val ANCHO_MINIATURA = 36.dp
private val ALTO_MINIATURA = 44.dp
private const val MILIS_POR_MINUTO = 60_000L
private const val MINUTOS_POR_HORA = 60L
private const val MINUTOS_POR_DIA = 24 * 60L

const val TAG_HOJA_DOCUMENTOS = "hoja_documentos"
const val TAG_LISTA_DOCUMENTOS = "docs_abiertos_lista"
const val TAG_ABRIR_OTRO = "docs_abrir_otro"
const val TAG_CERRAR_TODOS = "docs_cerrar_todos"

fun tagDocumento(id: String): String = "docs_abiertos_$id"

fun tagCerrarDocumento(id: String): String = "docs_abiertos_cerrar_$id"
