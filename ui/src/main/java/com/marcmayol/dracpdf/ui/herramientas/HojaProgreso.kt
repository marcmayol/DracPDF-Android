package com.marcmayol.dracpdf.ui.herramientas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * Lo que se enseña mientras una herramienta trabaja, y lo que queda cuando termina.
 *
 * La misma hoja para las tres cosas —en marcha, hecho y fallido— porque para el
 * usuario son un solo momento: pidió algo y espera saber en qué acabó. Tres pantallas
 * distintas obligarían a seguir el hilo entre ellas.
 *
 * No se puede cerrar tocando fuera mientras trabaja: con una operación larga en curso,
 * el gesto de descartar la hoja se lee como cancelar, y cancelar tiene su botón.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaProgreso(
    estado: EstadoHerramienta,
    alCancelar: () -> Unit,
    alCerrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val herramienta = estado.herramienta ?: return

    ModalBottomSheet(
        onDismissRequest = { if (!estado.enMarcha) alCerrar() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.testTag(TAG_HOJA_PROGRESO),
    ) {
        Column(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
        ) {
            Text(
                text = herramienta.etiqueta.replace("\n", " "),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            when {
                estado.error != null ->
                    Text(
                        text = estado.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(TAG_PROGRESO_ERROR),
                    )

                estado.resultado != null ->
                    Text(
                        text = estado.resultado,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag(TAG_PROGRESO_RESULTADO),
                    )

                else -> EnMarcha(estado)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = if (estado.enMarcha) alCancelar else alCerrar,
                    modifier = Modifier.heightIn(min = MedidasLadon.areaTactil).testTag(TAG_PROGRESO_ACCION),
                ) {
                    Text(if (estado.enMarcha) "Cancelar" else "Hecho")
                }
            }
        }
    }
}

@Composable
private fun EnMarcha(estado: EstadoHerramienta) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val fraccion = estado.fraccion
        if (fraccion == null) {
            // Todavía no se sabe cuánto hay que hacer: barra indeterminada antes que un
            // 0 % que no significa nada.
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag(TAG_PROGRESO_BARRA))
        } else {
            LinearProgressIndicator(
                progress = { fraccion },
                modifier = Modifier.fillMaxWidth().testTag(TAG_PROGRESO_BARRA),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (estado.total > 0) "${estado.hechas} de ${estado.total}" else "Preparando…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

const val TAG_HOJA_PROGRESO = "hoja_progreso"
const val TAG_PROGRESO_BARRA = "progreso_barra"
const val TAG_PROGRESO_ACCION = "progreso_accion"
const val TAG_PROGRESO_RESULTADO = "progreso_resultado"
const val TAG_PROGRESO_ERROR = "progreso_error"
