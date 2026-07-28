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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.marcmayol.dracpdf.ui.iconos.IconosLadon
import com.marcmayol.dracpdf.ui.tema.FormasLadon
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
    temaOscuro: Boolean = true,
    abiertos: List<DocumentoEnLista> = emptyList(),
    alElegirAbierto: (String) -> Unit = {},
    alCerrarAbierto: (String) -> Unit = {},
) {
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
            BotonIconoLadon(
                icono = IconosLadon.mas,
                descripcion = "Más acciones",
                alPulsar = {},
                habilitado = false,
                modifier = Modifier.testTag(TAG_MENU_INICIO),
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
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

private const val OPACIDAD_DRAGON = 0.07f

const val TAG_ABRIR = "inicio_abrir"
const val TAG_MENU_INICIO = "inicio_menu"
const val TAG_DRAGON = "inicio_vacio_dragon"
const val TAG_SECCION_ABIERTOS = "inicio_abiertos"
