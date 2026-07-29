package com.marcmayol.dracpdf.ui.visor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
 * La banda que avisa de lo que este formulario tiene de particular.
 *
 * Va con icono **y** texto, como los estados de firma: el color no puede ser el
 * único que cuente lo que pasa. Y se descarta, porque un aviso que no se puede
 * quitar deja de leerse a la tercera vez.
 */
@Composable
fun BandaAvisoFormulario(
    aviso: AvisoFormulario,
    alDescartar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .heightIn(min = MedidasLadon.areaTactil)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag(TAG_AVISO_FORMULARIO),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconoLadon(
            icono = IconosLadon.formulario,
            descripcion = null,
            estado = EstadoIcono.APAGADO,
        )
        Text(
            text = textoDe(aviso),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Entendido",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .heightIn(min = MedidasLadon.areaTactil)
                    .clickable(onClick = alDescartar)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .testTag(TAG_AVISO_DESCARTAR),
        )
    }
}

/**
 * Lo que se le dice al usuario, en su idioma y sin siglas donde se pueda evitar.
 *
 * El del híbrido es deliberadamente concreto: no basta con decir «puede verse
 * distinto», porque quien rellena un formulario oficial necesita saber **dónde** —en
 * Adobe— y **por qué** —el PDF lleva dos capas y Adobe dibuja la otra—, que es lo
 * que le permite comprobarlo antes de entregarlo.
 */
private fun textoDe(aviso: AvisoFormulario): String =
    when (aviso) {
        AvisoFormulario.NO_SE_PUEDE_RELLENAR ->
            "Este PDF usa un formulario XFA, que sólo rellena Adobe Acrobat. " +
                "Aquí se puede leer, pero no rellenar."

        AvisoFormulario.PUEDE_VERSE_DISTINTO_EN_ADOBE ->
            "Este PDF lleva dos capas de formulario. Se rellenará la compatible, " +
                "la que ven todos los visores; Adobe Acrobat podría seguir mostrando la otra."
    }

/**
 * Lo que ha salido mal, dicho y no tragado.
 *
 * Va abajo y no arriba porque llega como consecuencia de algo que se acaba de hacer
 * —guardar, rellenar— y ahí es donde está mirando el pulgar.
 */
@Composable
fun BandaError(
    mensaje: String,
    alDescartar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer)
                .heightIn(min = MedidasLadon.areaTactil)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag(TAG_BANDA_ERROR),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Cerrar",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier =
                Modifier
                    .heightIn(min = MedidasLadon.areaTactil)
                    .clickable(onClick = alDescartar)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .testTag(TAG_BANDA_ERROR_CERRAR),
        )
    }
}

const val TAG_AVISO_FORMULARIO = "visor_aviso_formulario"
const val TAG_AVISO_DESCARTAR = "visor_aviso_descartar"
const val TAG_BANDA_ERROR = "visor_banda_error"
const val TAG_BANDA_ERROR_CERRAR = "visor_banda_error_cerrar"
