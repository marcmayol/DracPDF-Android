package com.marcmayol.dracpdf.ui.firmas

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.ui.tema.ColoresPapel
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * La hoja de dibujar una firma: el lienzo, y qué hacer con lo dibujado.
 *
 * El lienzo va sobre papel y ocupa casi todo el alto de la hoja, porque una firma
 * hecha en un recuadro pequeño sale apretada y no se parece a la de uno. Se pide
 * apaisado por la misma razón: una firma es más ancha que alta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDibujarFirma(
    alGuardar: (FirmaDibujada) -> Unit,
    alCerrar: () -> Unit,
) {
    val lienzo = remember { EstadoLienzo() }
    // `hayTinta` se lee dentro de la composición para que los botones se enciendan en
    // cuanto el dedo levanta el primer trazo.
    var version by remember { mutableStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(TAG_HOJA_DIBUJAR),
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text(
                text = "Firma aquí",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(ALTO_LIENZO)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ColoresPapel.papel),
            ) {
                LienzoFirma(
                    estado = lienzo,
                    // Cada trazo terminado cambia la versión y con ella se recomponen
                    // los botones de abajo.
                    modifier = Modifier.testTag(TAG_LIENZO),
                )
                if (!lienzo.hayTinta) {
                    Text(
                        text = "Dibuja tu firma con el dedo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).testTag(TAG_PISTA),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Limpiar",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .heightIn(min = MedidasLadon.areaTactil)
                            .clickable {
                                lienzo.limpiar()
                                version++
                            }.padding(horizontal = 12.dp, vertical = 14.dp)
                            .testTag(TAG_LIMPIAR),
                )
                Text(
                    text = "Guardar firma",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .heightIn(min = MedidasLadon.areaTactil)
                            .clickable {
                                // Sin tinta no hay nada que guardar; se ignora en vez
                                // de guardar una firma en blanco que luego no se ve.
                                exportarFirma(lienzo.trazos)?.let(alGuardar)
                            }.padding(horizontal = 12.dp, vertical = 14.dp)
                            .testTag(TAG_GUARDAR_FIRMA),
                )
            }
        }
    }
}

private val ALTO_LIENZO = 200.dp

const val TAG_HOJA_DIBUJAR = "firma_hoja_dibujar"
const val TAG_LIMPIAR = "firma_limpiar"
const val TAG_GUARDAR_FIRMA = "firma_guardar"
const val TAG_PISTA = "firma_pista"
