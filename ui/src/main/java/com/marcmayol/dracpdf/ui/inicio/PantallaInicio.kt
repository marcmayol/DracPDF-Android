package com.marcmayol.dracpdf.ui.inicio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.ui.documentos.DocumentoEnLista
import com.marcmayol.dracpdf.ui.documentos.ListaDocumentos
import com.marcmayol.dracpdf.ui.iconos.BotonIconoLadon
import com.marcmayol.dracpdf.ui.iconos.EstadoIcono
import com.marcmayol.dracpdf.ui.iconos.IconoLadon
import com.marcmayol.dracpdf.ui.iconos.IconosLadon
import com.marcmayol.dracpdf.ui.tema.FormasLadon
import com.marcmayol.dracpdf.ui.tema.LocalTemaOscuro
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * La pantalla de cuando no hay nada abierto: el dragón atenuado, una frase y el
 * botón de abrir.
 *
 * La lista de recientes que dibuja el diseño llega en la Fase 7, junto con los
 * permisos persistidos que la hacen posible; enseñarla vacía y muerta ahora sería
 * peor que no enseñarla.
 */
@Composable
fun PantallaInicio(
    alAbrirPdf: () -> Unit,
    modifier: Modifier = Modifier,
    temaOscuro: Boolean = LocalTemaOscuro.current,
    abiertos: List<DocumentoEnLista> = emptyList(),
    alElegirAbierto: (String) -> Unit = {},
    alCerrarAbierto: (String) -> Unit = {},
    alAbrirTema: () -> Unit = {},
) {
    var menuAbierto by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // Lo mismo que hace la barra del visor: con edge-to-edge el fondo
                    // llega hasta arriba pero el contenido se aparta, o el dragón y el
                    // título se dibujan encima del reloj.
                    .statusBarsPadding()
                    .heightIn(min = MedidasLadon.barraSuperior)
                    .padding(horizontal = MedidasLadon.margen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(if (temaOscuro) IconosLadon.dragonBlanco else IconosLadon.dragonTinta),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = "DracPDF",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Box {
                BotonIconoLadon(
                    icono = IconosLadon.mas,
                    descripcion = "Más acciones",
                    alPulsar = { menuAbierto = true },
                    modifier = Modifier.testTag(TAG_MENU_INICIO),
                )
                MenuDelInicio(
                    abierto = menuAbierto,
                    alCerrar = { menuAbierto = false },
                    alElegirTema = {
                        menuAbierto = false
                        alAbrirTema()
                    },
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Y por abajo la de navegación: la lista de abiertos crece hacia el
                    // borde inferior y sin esto los últimos quedan bajo los botones del
                    // sistema.
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.testTag(TAG_DRAGON)) {
                Image(
                    painter = painterResource(if (temaOscuro) IconosLadon.dragonBlanco else IconosLadon.dragonTinta),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp).alpha(OPACIDAD_DRAGON),
                )
            }

            Text(
                text = "Abre un documento para empezar",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 22.dp),
            )

            Button(
                onClick = alAbrirPdf,
                shape = FormasLadon.large,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                modifier =
                    Modifier
                        .padding(top = 20.dp)
                        .heightIn(min = MedidasLadon.areaTactil)
                        .testTag(TAG_ABRIR),
            ) {
                Text(text = "Abrir PDF", style = MaterialTheme.typography.labelLarge)
            }

            Text(
                text = "o compártelo a DracPDF desde otra app",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )

            // «Abiertos» va encima de donde irán los recientes (Fase 7). Es la misma
            // lista que la hoja del visor y el mismo componente: dos sitios, un
            // diseño. Sólo aparece si queda algún documento abierto —por ejemplo tras
            // cerrar el que se estaba mirando teniendo otro detrás—.
            if (abiertos.isNotEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 34.dp)
                            .testTag(TAG_SECCION_ABIERTOS),
                ) {
                    Text(
                        text = "ABIERTOS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                    )
                    ListaDocumentos(
                        documentos = abiertos,
                        alElegir = alElegirAbierto,
                        alCerrarDocumento = alCerrarAbierto,
                    )
                }
            }
        }
    }
}

/**
 * El menú del ⋮, con las cuatro entradas de la maqueta.
 *
 * Sólo «Tema» funciona hoy; las otras tres se ven y no se pulsan, como el resto de lo
 * que aún no está: esconderlas haría que el menú cambiara de forma de una versión a
 * otra y obligara a volver a buscarlo todo.
 */
@Composable
private fun MenuDelInicio(
    abierto: Boolean,
    alCerrar: () -> Unit,
    alElegirTema: () -> Unit,
) {
    DropdownMenu(
        expanded = abierto,
        onDismissRequest = alCerrar,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        EntradaDelMenu("Tema", IconosLadon.tema, TAG_MENU_TEMA, alElegirTema)
        EntradaDelMenu("Ajustes", IconosLadon.ajustes, TAG_MENU_AJUSTES)
        EntradaDelMenu("Ayuda", IconosLadon.ayuda, TAG_MENU_AYUDA)
        EntradaDelMenu("Acerca de DracPDF", IconosLadon.acercaDe, TAG_MENU_ACERCA)
    }
}

@Composable
private fun EntradaDelMenu(
    etiqueta: String,
    icono: Int,
    tag: String,
    alPulsar: (() -> Unit)? = null,
) {
    DropdownMenuItem(
        text = { Text(etiqueta, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = {
            IconoLadon(
                icono = icono,
                descripcion = null,
                estado = if (alPulsar == null) EstadoIcono.DESHABILITADO else EstadoIcono.APAGADO,
            )
        },
        enabled = alPulsar != null,
        onClick = alPulsar ?: {},
        modifier = Modifier.testTag(tag),
    )
}

private const val OPACIDAD_DRAGON = 0.07f

const val TAG_ABRIR = "inicio_abrir"
const val TAG_MENU_INICIO = "inicio_menu"
const val TAG_MENU_TEMA = "inicio_menu_tema"
const val TAG_MENU_AJUSTES = "inicio_menu_ajustes"
const val TAG_MENU_AYUDA = "inicio_menu_ayuda"
const val TAG_MENU_ACERCA = "inicio_menu_acerca"
const val TAG_DRAGON = "inicio_vacio_dragon"
const val TAG_SECCION_ABIERTOS = "inicio_abiertos"
