package com.marcmayol.dracpdf.ui.ajustes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.marcmayol.dracpdf.ui.iconos.EstadoIcono
import com.marcmayol.dracpdf.ui.iconos.IconoLadon
import com.marcmayol.dracpdf.ui.iconos.IconosLadon
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * Los ajustes de la aplicación.
 *
 * Hoy tiene una sola sección de verdad —hacer DracPDF el lector por defecto— y está
 * escrita para quien no sabe qué es un «intent»: el mecanismo de Android para elegir
 * aplicación predeterminada es de las cosas peor explicadas del sistema, y quien
 * quiere que sus PDF se abran aquí no tiene por qué entenderlo, sólo conseguirlo.
 *
 * El botón lleva a los ajustes del sistema porque **no hay otra forma**: ninguna
 * aplicación puede declararse predeterminada a sí misma, y prometerlo con un
 * interruptor propio sería mentir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaAjustes(
    alAbrirAjustesDelSistema: () -> Unit,
    alCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    esElPredeterminado: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.testTag(TAG_HOJA_AJUSTES),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
        ) {
            Text(
                text = "Ajustes",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Text(
                text = "LECTOR POR DEFECTO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )

            Text(
                text =
                    if (esElPredeterminado) {
                        "Los PDF que abras desde otras aplicaciones ya se abren aquí."
                    } else {
                        "Para que los PDF se abran siempre en DracPDF, abre uno desde otra aplicación " +
                            "—el correo, un chat, el gestor de archivos— y en el selector que sale elige " +
                            "DracPDF y toca «Siempre».\n\n" +
                            "Si ese selector ya no aparece es que otro lector se quedó de predeterminado: " +
                            "entra en sus ajustes de aplicación y borra sus valores predeterminados."
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FilaDeAjuste(
                titulo = "Abrir los ajustes de esta aplicación",
                explicacion = "Ahí está «Abrir de forma predeterminada», que es donde el sistema lo decide.",
                icono = IconosLadon.ajustes,
                tag = TAG_AJUSTES_SISTEMA,
                alPulsar = alAbrirAjustesDelSistema,
            )
        }
    }
}

@Composable
private fun FilaDeAjuste(
    titulo: String,
    explicacion: String,
    icono: Int,
    tag: String,
    alPulsar: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = MedidasLadon.areaTactil)
                .clickable(onClick = alPulsar)
                .padding(vertical = 12.dp)
                .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconoLadon(icono = icono, descripcion = null, estado = EstadoIcono.ACENTO)
        Column {
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = explicacion,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

const val TAG_HOJA_AJUSTES = "hoja_ajustes"
const val TAG_AJUSTES_SISTEMA = "ajustes_abrir_sistema"
