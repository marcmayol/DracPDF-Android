package com.marcmayol.dracpdf.ui.visor

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.ui.iconos.EstadoIcono
import com.marcmayol.dracpdf.ui.iconos.IconoLadon
import com.marcmayol.dracpdf.ui.iconos.IconosLadon
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * La hoja de la vista: cómo se encaja la página, si van de dos en dos y hacia dónde
 * mira lo que se ve.
 *
 * Es una hoja y no una barra a propósito. La regla del diseño —**un modo, una barra**—
 * deja los cuatro sitios de la barra inferior para lo que se abre a mitad de lectura, y
 * los cuatro de la superior para atrás · nombre · buscar · ⋮; meter aquí tres controles
 * más habría obligado a quitar uno de los ocho o a inventar una segunda barra, que es
 * justo lo que la regla prohíbe. Se entra por el ⋮, como todo lo que no cabe.
 *
 * Los tres ajustes se aplican al momento y sin botón de confirmar: son reversibles de
 * un toque y se ven detrás de la hoja mientras se eligen, así que preguntarle al lector
 * si está seguro sobraría.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaVista(
    vista: VistaDelVisor,
    alAjustar: (AjusteDeVista) -> Unit,
    alAlternarDoblePagina: () -> Unit,
    alGirar: () -> Unit,
    alCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    cabenDosPaginas: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.testTag(TAG_HOJA_VISTA),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = "Vista",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            Column(modifier = Modifier.selectableGroup()) {
                AJUSTES.forEach { (ajuste, etiqueta) ->
                    FilaDeAjuste(ajuste = ajuste, etiqueta = etiqueta, elegido = vista.ajuste, alElegir = alAjustar)
                }
            }

            FilaDeDoblePagina(
                puesta = vista.doblePagina,
                cabenDos = cabenDosPaginas,
                alAlternar = alAlternarDoblePagina,
            )

            FilaDeGiro(giro = vista.giro, alGirar = alGirar)

            Text(
                text = "Girar la vista no toca el documento: para girar las páginas de verdad, Organizar.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 20.dp),
            )
        }
    }
}

@Composable
private fun FilaDeAjuste(
    ajuste: AjusteDeVista,
    etiqueta: String,
    elegido: AjusteDeVista,
    alElegir: (AjusteDeVista) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = MedidasLadon.areaTactil)
                // La fila entera es el área táctil y el lector de pantalla anuncia un
                // grupo de radios, igual que en la hoja del tema.
                .selectable(
                    selected = ajuste == elegido,
                    role = Role.RadioButton,
                    onClick = { alElegir(ajuste) },
                ).padding(horizontal = 20.dp)
                .testTag(tagAjusteDeVista(ajuste)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = ajuste == elegido, onClick = null)
        IconoLadon(icono = iconoDe(ajuste), descripcion = null, estado = EstadoIcono.APAGADO)
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Dos páginas lado a lado, y por qué a veces no se puede.
 *
 * Cuando no caben la fila **se queda a la vista, apagada**, con el motivo debajo. Es la
 * misma decisión que tomó el destino «Formulario» de la barra inferior: una opción que
 * desaparece deja a quien la buscaba pensando que se la ha inventado, y aquí además
 * volvería sola al girar el teléfono, que parecería un fallo.
 */
@Composable
private fun FilaDeDoblePagina(
    puesta: Boolean,
    cabenDos: Boolean,
    alAlternar: () -> Unit,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MedidasLadon.areaTactil)
                    .clickable(enabled = cabenDos, onClick = alAlternar)
                    .padding(horizontal = 20.dp)
                    .semantics { role = Role.Switch }
                    .testTag(TAG_VISTA_DOBLE),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconoLadon(
                icono = if (puesta && cabenDos) IconosLadon.paginaDoble else IconosLadon.paginaSimple,
                descripcion = null,
                estado = if (cabenDos) EstadoIcono.APAGADO else EstadoIcono.DESHABILITADO,
            )
            Text(
                text = "Doble página",
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (cabenDos) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = ALFA_APAGADO)
                    },
                modifier = Modifier.weight(1f),
            )
            Switch(checked = puesta && cabenDos, onCheckedChange = null, enabled = cabenDos)
        }
        if (!cabenDos) {
            Text(
                text = "Aquí no caben dos: gira el teléfono o ábrelo en una pantalla más ancha.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp).testTag(TAG_VISTA_DOBLE_NO_CABE),
            )
        }
    }
}

@Composable
private fun FilaDeGiro(
    giro: GiroDeVista,
    alGirar: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = MedidasLadon.areaTactil)
                .clickable(onClick = alGirar)
                .padding(horizontal = 20.dp)
                .semantics { role = Role.Button }
                .testTag(TAG_VISTA_GIRAR),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconoLadon(icono = IconosLadon.rotar, descripcion = null, estado = EstadoIcono.APAGADO)
        Text(
            text = "Girar la vista",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // El ángulo puesto se dice con un número y no con la inclinación del icono: a
        // 180° un glifo girado se parece demasiado al de 0°.
        Text(
            text = "${giro.grados.toInt()}°",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag(TAG_VISTA_GIRO_PUESTO),
        )
    }
}

private fun iconoDe(ajuste: AjusteDeVista): Int =
    when (ajuste) {
        AjusteDeVista.ANCHO -> IconosLadon.ajustarAncho
        AjusteDeVista.PAGINA -> IconosLadon.ajustarPagina
    }

private val AJUSTES =
    listOf(
        AjusteDeVista.ANCHO to "Ajustar al ancho",
        AjusteDeVista.PAGINA to "Página completa",
    )

private const val ALFA_APAGADO = 0.38f

const val TAG_HOJA_VISTA = "visor_hoja_vista"
const val TAG_VISTA_DOBLE = "vista_doble_pagina"
const val TAG_VISTA_DOBLE_NO_CABE = "vista_doble_pagina_no_cabe"
const val TAG_VISTA_GIRAR = "vista_girar"
const val TAG_VISTA_GIRO_PUESTO = "vista_giro_puesto"

fun tagAjusteDeVista(ajuste: AjusteDeVista): String = "vista_ajuste_${ajuste.name.lowercase()}"
