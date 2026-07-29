package com.marcmayol.dracpdf.ui.visor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.ui.iconos.EstadoIcono
import com.marcmayol.dracpdf.ui.iconos.IconoLadon
import com.marcmayol.dracpdf.ui.iconos.IconosLadon
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * Las opciones de un combo o de una lista, en una hoja.
 *
 * Un desplegable dibujado dentro del papel sería del tamaño del campo —a veces cuatro
 * milímetros— y habría que acertarle con el dedo. La hoja es de la interfaz, no del
 * documento, así que aquí sí manda el tema y sí se usan las medidas táctiles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaOpciones(
    campo: CampoFormulario,
    alElegir: (String) -> Unit,
    alCerrar: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = rememberModalBottomSheetState(),
        modifier = Modifier.testTag(TAG_HOJA_OPCIONES),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = campo.etiqueta ?: campo.nombre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn {
                items(campo.opciones) { opcion ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = MedidasLadon.areaTactil)
                                .clickable { alElegir(opcion) }
                                .padding(horizontal = 20.dp)
                                .testTag(tagOpcion(opcion)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // La elegida lleva marca **y** no sólo color: el estado no puede
                        // depender de distinguir dos tonos.
                        if (opcion == campo.valor) {
                            IconoLadon(
                                icono = IconosLadon.comprobado,
                                descripcion = "Elegida",
                                estado = EstadoIcono.ACENTO,
                            )
                        } else {
                            Text(text = "", modifier = Modifier.padding(start = MedidasLadon.areaTactil / 2))
                        }
                        Text(
                            text = opcion,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

const val TAG_HOJA_OPCIONES = "visor_hoja_opciones"

fun tagOpcion(opcion: String): String = "opcion_$opcion"
