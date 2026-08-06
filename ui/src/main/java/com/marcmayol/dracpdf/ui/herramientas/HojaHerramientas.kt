package com.marcmayol.dracpdf.ui.herramientas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.ui.iconos.EstadoIcono
import com.marcmayol.dracpdf.ui.iconos.IconoLadon
import com.marcmayol.dracpdf.ui.tema.FormasLadon

/**
 * La caja de herramientas, en rejilla de tres.
 *
 * Lo que aún no está se ve apagado y no desaparece: un menú que cambia de forma entre
 * versiones obliga a volver a buscarlo todo. Con un documento firmado se apaga además
 * todo lo que reescribiría el PDF —**convertir no**, que sólo lee— porque el resultado
 * saldría con las firmas rotas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaHerramientas(
    alElegir: (Herramienta) -> Unit,
    alCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    documentoFirmado: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.testTag(TAG_HOJA_HERRAMIENTAS),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = "Herramientas",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            if (documentoFirmado) {
                Text(
                    text = "Este documento está firmado: lo que reescribiría el PDF queda fuera.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp).testTag(TAG_AVISO_FIRMADO),
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNAS),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag(TAG_REJILLA_HERRAMIENTAS),
            ) {
                items(Herramienta.entries) { herramienta ->
                    val disponible = herramienta.disponible(documentoFirmado)
                    Celda(
                        herramienta = herramienta,
                        habilitada = disponible,
                        alPulsar = { alElegir(herramienta) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Celda(
    herramienta: Herramienta,
    habilitada: Boolean,
    alPulsar: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = FormasLadon.medium,
        modifier =
            Modifier
                .aspectRatio(1f)
                .clickable(enabled = habilitada, onClick = alPulsar)
                .testTag(herramienta.tag),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            IconoLadon(
                icono = herramienta.icono,
                descripcion = null,
                estado = if (habilitada) EstadoIcono.APAGADO else EstadoIcono.DESHABILITADO,
            )
            Text(
                text = herramienta.etiqueta,
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (habilitada) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = ALFA_DESHABILITADO)
                    },
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private const val COLUMNAS = 3
private const val ALFA_DESHABILITADO = 0.38f

const val TAG_HOJA_HERRAMIENTAS = "hoja_herramientas"
const val TAG_REJILLA_HERRAMIENTAS = "herramientas_rejilla"
const val TAG_AVISO_FIRMADO = "herramientas_aviso_firmado"
