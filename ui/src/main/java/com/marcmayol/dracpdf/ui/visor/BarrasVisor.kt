package com.marcmayol.dracpdf.ui.visor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.ui.iconos.BotonIconoLadon
import com.marcmayol.dracpdf.ui.iconos.EstadoIcono
import com.marcmayol.dracpdf.ui.iconos.IconoLadon
import com.marcmayol.dracpdf.ui.iconos.IconosLadon
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * Barra superior, 56 dp: sólo identidad y acción de página. Atrás · nombre del
 * archivo elidido · buscar · ⋮. Nada más; el resto vive un toque más abajo.
 */
@Composable
fun BarraSuperiorVisor(
    nombre: String,
    alSalir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                // Edge-to-edge dibuja bajo la barra de estado: sin esto el título
                // aparece debajo del reloj. El fondo sí llega hasta arriba —por eso el
                // padding va después del color— y sólo el contenido se aparta.
                .statusBarsPadding()
                .heightIn(min = MedidasLadon.barraSuperior)
                .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotonIconoLadon(
            icono = IconosLadon.atras,
            descripcion = "Cerrar el documento",
            alPulsar = alSalir,
            modifier = Modifier.testTag(TAG_ATRAS),
        )
        Text(
            text = nombre,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp).testTag(TAG_TITULO),
        )
        BotonIconoLadon(
            icono = IconosLadon.buscar,
            descripcion = "Buscar en el documento",
            alPulsar = {},
            // Llega en la Fase 7. Se ve y no se pulsa: una acción que desaparece deja
            // al usuario buscándola, y el inventario exige poder comprobar su estado.
            habilitado = false,
            modifier = Modifier.testTag(TAG_BUSCAR),
        )
        BotonIconoLadon(
            icono = IconosLadon.mas,
            descripcion = "Más acciones",
            alPulsar = {},
            habilitado = false,
            modifier = Modifier.testTag(TAG_MENU),
        )
    }
}

/**
 * Barra inferior, 80 dp, cuatro acciones: Índice · Formulario · Herramientas ·
 * Firmas. Son las cuatro cosas que se abren a mitad de lectura, y el pulgar llega a
 * todas. Es una barra de acciones, no de navegación: no hay secciones entre las que
 * moverse, se abren hojas.
 */
@Composable
fun BarraInferiorVisor(
    alAbrirIndice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                // Igual que arriba: el color llega hasta el borde y el contenido se
                // aparta de la barra de navegación del sistema.
                .navigationBarsPadding()
                .heightIn(min = MedidasLadon.barraInferior),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DestinoInferior(
            icono = IconosLadon.marcador,
            etiqueta = "Índice",
            tag = TAG_DESTINO_INDICE,
            habilitado = true,
            alPulsar = alAbrirIndice,
            modifier = Modifier.weight(1f),
        )
        DestinoInferior(
            icono = IconosLadon.formulario,
            etiqueta = "Formulario",
            tag = TAG_DESTINO_FORMULARIO,
            habilitado = false,
            modifier = Modifier.weight(1f),
        )
        DestinoInferior(
            icono = IconosLadon.herramientas,
            etiqueta = "Herramientas",
            tag = TAG_DESTINO_HERRAMIENTAS,
            habilitado = false,
            modifier = Modifier.weight(1f),
        )
        DestinoInferior(
            icono = IconosLadon.firmaCertificado,
            etiqueta = "Firmas",
            tag = TAG_DESTINO_FIRMAS,
            habilitado = false,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DestinoInferior(
    icono: Int,
    etiqueta: String,
    tag: String,
    habilitado: Boolean,
    modifier: Modifier = Modifier,
    alPulsar: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier =
            modifier
                // Reparto a partes iguales en vez de ancho fijo: con 76 dp clavados,
                // «Herramientas» se quedaba en «Herramienta».
                .heightIn(min = MedidasLadon.areaTactil)
                .clickable(enabled = habilitado, onClick = alPulsar)
                .semantics { role = Role.Tab }
                .testTag(tag),
    ) {
        IconoLadon(
            icono = icono,
            descripcion = null,
            estado = if (habilitado) EstadoIcono.APAGADO else EstadoIcono.DESHABILITADO,
        )
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelSmall,
            color =
                if (habilitado) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = ALFA_DESHABILITADO)
                },
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

private const val ALFA_DESHABILITADO = 0.38f

const val TAG_ATRAS = "visor_atras"
const val TAG_TITULO = "visor_titulo"
const val TAG_BUSCAR = "visor_buscar"
const val TAG_MENU = "visor_menu"
const val TAG_DESTINO_INDICE = "visor_destino_indice"
const val TAG_DESTINO_FORMULARIO = "visor_destino_formulario"
const val TAG_DESTINO_HERRAMIENTAS = "visor_destino_herramientas"
const val TAG_DESTINO_FIRMAS = "visor_destino_firmas"
