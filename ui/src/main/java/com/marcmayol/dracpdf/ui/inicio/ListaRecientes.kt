package com.marcmayol.dracpdf.ui.inicio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.ui.iconos.BotonIconoLadon
import com.marcmayol.dracpdf.ui.iconos.EstadoIcono
import com.marcmayol.dracpdf.ui.iconos.IconoLadon
import com.marcmayol.dracpdf.ui.iconos.IconosLadon
import com.marcmayol.dracpdf.ui.tema.FormasLadon
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * La lista de recientes del inicio.
 *
 * Sin miniaturas, a diferencia de los abiertos: un documento reciente puede estar
 * cerrado, en la nube o sin permiso, y rasterizar su primera página obligaría a
 * abrirlo entero para adornar una fila. La fecha y la página dicen más y no cuestan
 * nada.
 */
@Composable
fun ListaRecientes(
    recientes: List<RecienteEnLista>,
    alElegir: (String) -> Unit,
    alOlvidar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 24.dp).testTag(TAG_SECCION_RECIENTES),
    ) {
        Text(
            text = "RECIENTES",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        recientes.forEach { reciente ->
            FilaReciente(
                reciente = reciente,
                alElegir = { alElegir(reciente.identificador) },
                alOlvidar = { alOlvidar(reciente.identificador) },
            )
        }
    }
}

@Composable
private fun FilaReciente(
    reciente: RecienteEnLista,
    alElegir: () -> Unit,
    alOlvidar: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = FormasLadon.medium,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clickable(onClick = alElegir)
                .testTag(tagReciente(reciente.identificador)),
    ) {
        Row(
            modifier = Modifier.heightIn(min = MedidasLadon.areaTactil).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconoLadon(
                icono = IconosLadon.reciente,
                descripcion = null,
                estado = if (reciente.puedeQueNoAbra) EstadoIcono.DESHABILITADO else EstadoIcono.APAGADO,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reciente.nombre,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        listOfNotNull(
                            reciente.cuando,
                            reciente.porDonde,
                            "puede que ya no se abra".takeIf { reciente.puedeQueNoAbra },
                        ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BotonIconoLadon(
                icono = IconosLadon.cerrar,
                descripcion = "Quitar de recientes",
                alPulsar = alOlvidar,
                modifier = Modifier.testTag(tagOlvidarReciente(reciente.identificador)),
            )
        }
    }
}

const val TAG_SECCION_RECIENTES = "inicio_recientes"

fun tagReciente(identificador: String): String = "reciente_${identificador.hashCode()}"

fun tagOlvidarReciente(identificador: String): String = "reciente_olvidar_${identificador.hashCode()}"
