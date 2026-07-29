package com.marcmayol.dracpdf.ui.firmas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.dominio.modelo.Firma
import com.marcmayol.dracpdf.ui.iconos.BotonIconoLadon
import com.marcmayol.dracpdf.ui.iconos.EstadoIcono
import com.marcmayol.dracpdf.ui.iconos.IconoLadon
import com.marcmayol.dracpdf.ui.iconos.IconosLadon
import com.marcmayol.dracpdf.ui.tema.ColoresPapel
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * La biblioteca de firmas: las que ya hay, y la puerta para dibujar otra.
 *
 * Las firmas se enseñan sobre papel y no sobre el fondo del tema. Una firma en tinta
 * oscura sobre una hoja en modo oscuro no se vería, y además así se ve exactamente
 * como quedará en el documento, que es lo único que importa al elegirla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaFirmas(
    firmas: List<Firma>,
    miniaturas: Map<String, ImageBitmap>,
    alElegir: (Firma) -> Unit,
    alBorrar: (Firma) -> Unit,
    alDibujarNueva: () -> Unit,
    alCerrar: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = rememberModalBottomSheetState(),
        modifier = Modifier.testTag(TAG_HOJA_FIRMAS),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Firmas",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier =
                        Modifier
                            .heightIn(min = MedidasLadon.areaTactil)
                            .clickable(onClick = alDibujarNueva)
                            .padding(horizontal = 8.dp)
                            .testTag(TAG_DIBUJAR_NUEVA),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IconoLadon(icono = IconosLadon.firmaDibujada, descripcion = null, estado = EstadoIcono.ACENTO)
                    Text(
                        text = "Dibujar",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (firmas.isEmpty()) {
                Text(
                    text = "Todavía no hay ninguna firma. Dibuja una y quedará guardada para las próximas veces.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).testTag(TAG_SIN_FIRMAS),
                )
            } else {
                LazyColumn {
                    items(firmas, key = { it.id.valor }) { firma ->
                        FilaFirma(
                            firma = firma,
                            miniatura = miniaturas[firma.id.valor],
                            alElegir = { alElegir(firma) },
                            alBorrar = { alBorrar(firma) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaFirma(
    firma: Firma,
    miniatura: ImageBitmap?,
    alElegir: () -> Unit,
    alBorrar: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = MedidasLadon.areaTactil)
                .clickable(onClick = alElegir)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag(tagFirma(firma.id.valor)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = ANCHO_MINIATURA, height = ALTO_MINIATURA)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ColoresPapel.papel),
            contentAlignment = Alignment.Center,
        ) {
            if (miniatura != null) {
                Image(
                    bitmap = miniatura,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(ALTO_MINIATURA).padding(4.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Text(
            text = firma.nombre,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        BotonIconoLadon(
            icono = IconosLadon.eliminar,
            descripcion = "Borrar la firma ${firma.nombre}",
            alPulsar = alBorrar,
            modifier = Modifier.testTag(tagBorrarFirma(firma.id.valor)),
        )
    }
}

private val ANCHO_MINIATURA = 96.dp
private val ALTO_MINIATURA = 48.dp

const val TAG_HOJA_FIRMAS = "firmas_hoja"
const val TAG_DIBUJAR_NUEVA = "firmas_dibujar_nueva"
const val TAG_SIN_FIRMAS = "firmas_vacio"

fun tagFirma(id: String): String = "firma_$id"

fun tagBorrarFirma(id: String): String = "firma_borrar_$id"
