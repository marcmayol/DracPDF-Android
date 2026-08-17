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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.dominio.modelo.Anotacion
import com.marcmayol.dracpdf.dominio.modelo.TipoAnotacion
import com.marcmayol.dracpdf.ui.iconos.BotonIconoLadon
import com.marcmayol.dracpdf.ui.iconos.EstadoIcono
import com.marcmayol.dracpdf.ui.iconos.IconoLadon
import com.marcmayol.dracpdf.ui.iconos.IconosLadon
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * Las marcas de la página, para poder quitarlas.
 *
 * Es la mitad que falta de marcar: el criterio de la fase pide que las anotaciones
 * sean **eliminables**, y sobre la página sólo se ve un resaltado amarillo que no
 * invita a nada. Aquí se listan las de la página que se está mirando, cada una con lo
 * que dice y un botón de quitar.
 *
 * Sólo las de la página actual, y no las del documento entero: recorrer quinientas
 * páginas para llenar una lista que se mira dos segundos sería el mismo error que el
 * visor evita al rasterizar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaAnotaciones(
    pagina: Int,
    anotaciones: List<Anotacion>,
    alBorrar: (Anotacion) -> Unit,
    alCerrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.testTag(TAG_HOJA_ANOTACIONES),
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text(
                text = "Marcas de la página ${pagina + 1}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            if (anotaciones.isEmpty()) {
                Text(
                    text =
                        "Esta página no tiene marcas. Mantén pulsado un texto para resaltarlo, " +
                            "subrayarlo o tacharlo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 20.dp).testTag(TAG_SIN_ANOTACIONES),
                )
            } else {
                LazyColumn(modifier = Modifier.padding(bottom = 12.dp)) {
                    items(anotaciones, key = { it.posicion }) { anotacion ->
                        FilaDeMarca(anotacion = anotacion, alBorrar = { alBorrar(anotacion) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaDeMarca(
    anotacion: Anotacion,
    alBorrar: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = MedidasLadon.areaTactil)
                .clickable(onClick = alBorrar)
                .testTag(tagAnotacion(anotacion.posicion)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconoLadon(icono = iconoDe(anotacion.tipo), descripcion = null, estado = EstadoIcono.APAGADO)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nombreDe(anotacion.tipo),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (anotacion.contenido.isNotBlank()) {
                Text(
                    text = anotacion.contenido,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        BotonIconoLadon(
            icono = IconosLadon.eliminar,
            descripcion = "Quitar esta marca",
            alPulsar = alBorrar,
            modifier = Modifier.testTag(tagBorrarAnotacion(anotacion.posicion)),
        )
    }
}

private fun nombreDe(tipo: TipoAnotacion): String =
    when (tipo) {
        TipoAnotacion.RESALTADO -> "Resaltado"
        TipoAnotacion.SUBRAYADO -> "Subrayado"
        TipoAnotacion.TACHADO -> "Tachado"
        TipoAnotacion.NOTA -> "Nota"
        TipoAnotacion.TEXTO -> "Texto añadido"
    }

private fun iconoDe(tipo: TipoAnotacion): Int =
    when (tipo) {
        TipoAnotacion.RESALTADO -> IconosLadon.resaltar
        TipoAnotacion.SUBRAYADO -> IconosLadon.subrayar
        TipoAnotacion.TACHADO -> IconosLadon.tachar
        TipoAnotacion.NOTA -> IconosLadon.nota
        TipoAnotacion.TEXTO -> IconosLadon.anadirTexto
    }

const val TAG_HOJA_ANOTACIONES = "hoja_anotaciones"
const val TAG_SIN_ANOTACIONES = "anotaciones_ninguna"

fun tagAnotacion(posicion: Int): String = "anotacion_$posicion"

fun tagBorrarAnotacion(posicion: Int): String = "anotacion_borrar_$posicion"
