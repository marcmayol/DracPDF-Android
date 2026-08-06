package com.marcmayol.dracpdf.ui.tema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * La hoja del tema: tres radios y nada más, como manda el diseño.
 *
 * «Del sistema» es una opción de pleno derecho y no la ausencia de las otras dos: en
 * un teléfono que oscurece al anochecer, seguir al sistema es lo que la mayoría
 * quiere, y tiene que poder volverse a ello después de haber elegido a mano.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaTema(
    elegida: PreferenciaTema,
    alElegir: (PreferenciaTema) -> Unit,
    alCerrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.testTag(TAG_HOJA_TEMA),
    ) {
        Column(modifier = Modifier.navigationBarsPadding().selectableGroup()) {
            Text(
                text = "Tema",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            OPCIONES.forEach { (preferencia, etiqueta) ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = MedidasLadon.areaTactil)
                            // `selectable` con rol de radio y no un `clickable` suelto:
                            // así la fila entera es el área táctil y el lector de
                            // pantalla anuncia el grupo, no tres botones sueltos.
                            .selectable(
                                selected = preferencia == elegida,
                                role = Role.RadioButton,
                                onClick = { alElegir(preferencia) },
                            ).padding(horizontal = 20.dp)
                            .testTag(tagTema(preferencia)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = preferencia == elegida, onClick = null)
                    Text(
                        text = etiqueta,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Text(
                text = "El documento se ve siempre sobre papel claro, en los dos temas.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 20.dp),
            )
        }
    }
}

private val OPCIONES =
    listOf(
        PreferenciaTema.CLARO to "Claro",
        PreferenciaTema.OSCURO to "Oscuro",
        PreferenciaTema.SISTEMA to "Del sistema",
    )

const val TAG_HOJA_TEMA = "inicio_hoja_tema"

fun tagTema(preferencia: PreferenciaTema): String = "tema_${preferencia.name.lowercase()}"
