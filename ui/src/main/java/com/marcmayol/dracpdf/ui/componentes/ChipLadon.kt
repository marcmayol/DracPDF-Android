package com.marcmayol.dracpdf.ui.componentes

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.ui.tema.FormaChip

/**
 * Un chip de elegir una cosa entre varias, con la selección en acento.
 *
 * Existe porque el `FilterChip` de serie deja el elegido **sin relleno y sin borde**
 * sobre las superficies de este tema: el que estaba seleccionado se veía como texto
 * suelto y los otros tres, con su contorno, parecían los activos. Justo al revés.
 */
@Composable
fun ChipLadon(
    texto: String,
    elegido: Boolean,
    alPulsar: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = elegido,
        onClick = alPulsar,
        shape = FormaChip,
        label = { Text(texto, style = MaterialTheme.typography.labelLarge) },
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = elegido,
                borderColor = MaterialTheme.colorScheme.outline,
                selectedBorderColor = MaterialTheme.colorScheme.primary,
                selectedBorderWidth = GROSOR_ELEGIDO,
            ),
        modifier = modifier.testTag(tag),
    )
}

private val GROSOR_ELEGIDO = 1.dp
