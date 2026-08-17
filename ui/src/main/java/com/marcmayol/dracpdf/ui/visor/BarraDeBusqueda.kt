package com.marcmayol.dracpdf.ui.visor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.ui.iconos.BotonIconoLadon
import com.marcmayol.dracpdf.ui.iconos.IconosLadon
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * La barra de buscar, que **sustituye** a la del documento mientras se busca.
 *
 * Es la regla del diseño de siempre —nunca dos barras superiores— y aquí además
 * resuelve un problema de sitio: el campo de texto, el contador y las dos flechas no
 * caben junto al nombre del archivo en 56 dp de pantalla de móvil.
 *
 * El teclado se abre solo al entrar. Quien pulsa la lupa ya sabe que va a escribir, y
 * obligarle a un toque más en el campo es el tipo de fricción que hace que la
 * búsqueda parezca lenta aunque no lo sea.
 */
@Composable
fun BarraDeBusqueda(
    busqueda: Busqueda,
    alEscribir: (String) -> Unit,
    alAnterior: () -> Unit,
    alSiguiente: () -> Unit,
    alCerrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .statusBarsPadding()
                .heightIn(min = MedidasLadon.barraSuperior)
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .testTag(TAG_BARRA_BUSQUEDA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BotonIconoLadon(
            icono = IconosLadon.atras,
            descripcion = "Dejar de buscar",
            alPulsar = alCerrar,
            modifier = Modifier.testTag(TAG_BUSQUEDA_CERRAR),
        )
        OutlinedTextField(
            value = busqueda.termino,
            onValueChange = alEscribir,
            singleLine = true,
            placeholder = { Text("Buscar en el documento") },
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // La tecla de buscar del teclado hace lo mismo que la flecha: llevar a la
            // siguiente. Lo que se busca ya se está buscando mientras se escribe.
            keyboardActions = KeyboardActions(onSearch = { alSiguiente() }),
            modifier = Modifier.weight(1f).focusRequester(foco).testTag(TAG_BUSQUEDA_CAMPO),
        )
        if (busqueda.contador.isNotEmpty()) {
            Text(
                text = busqueda.contador,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp).testTag(TAG_BUSQUEDA_CONTADOR),
            )
        }
        BotonIconoLadon(
            icono = IconosLadon.paginaAnterior,
            descripcion = "Coincidencia anterior",
            alPulsar = alAnterior,
            habilitado = busqueda.hayResultados,
            modifier = Modifier.testTag(TAG_BUSQUEDA_ANTERIOR),
        )
        BotonIconoLadon(
            icono = IconosLadon.paginaSiguiente,
            descripcion = "Coincidencia siguiente",
            alPulsar = alSiguiente,
            habilitado = busqueda.hayResultados,
            modifier = Modifier.testTag(TAG_BUSQUEDA_SIGUIENTE),
        )
    }
}

const val TAG_BARRA_BUSQUEDA = "visor_barra_busqueda"
const val TAG_BUSQUEDA_CAMPO = "busqueda_campo"
const val TAG_BUSQUEDA_CONTADOR = "busqueda_contador"
const val TAG_BUSQUEDA_ANTERIOR = "busqueda_anterior"
const val TAG_BUSQUEDA_SIGUIENTE = "busqueda_siguiente"
const val TAG_BUSQUEDA_CERRAR = "busqueda_cerrar"
