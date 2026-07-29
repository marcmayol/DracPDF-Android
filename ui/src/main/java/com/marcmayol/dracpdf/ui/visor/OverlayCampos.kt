package com.marcmayol.dracpdf.ui.visor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.IdCampo
import com.marcmayol.dracpdf.dominio.modelo.TamanoPt
import com.marcmayol.dracpdf.dominio.modelo.TipoCampo
import com.marcmayol.dracpdf.ui.tema.ColoresPapel

/**
 * Los campos de formulario de una página, dibujados encima de su render.
 *
 * **El overlay es papel, no interfaz.** Los colores salen de [ColoresPapel] y no del
 * tema: el documento se ve igual en claro y en oscuro, y un campo resaltado en ámbar
 * lo está también de noche. Si esto usara `colorScheme`, el formulario cambiaría de
 * aspecto al cambiar el tema del teléfono, que es justo lo que un documento no hace.
 *
 * **La transformación es proporcional a propósito.** Un campo se coloca por su
 * fracción de página —«a un tercio del ancho, a media altura»— y no por píxeles
 * calculados a una escala concreta. Así el campo sigue alineado después de un
 * pellizco sin que nadie tenga que recalcular nada: la caja de la página crece y el
 * campo crece con ella.
 *
 * MuPDF entrega los límites del campo ya en el sistema de la página —origen arriba a
 * la izquierda, con la rotación aplicada—, no en el del PDF, que mide desde abajo.
 * Es el mismo sistema en el que se rasteriza, así que aquí no hay que darle la
 * vuelta a nada; hacerlo pondría todos los campos boca abajo.
 */
@Composable
fun OverlayCampos(
    campos: List<CampoFormulario>,
    tamano: TamanoPt,
    ancho: Dp,
    alto: Dp,
    campoActivo: IdCampo?,
    alTocarCampo: (CampoFormulario) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        campos.forEach { campo ->
            CampoDibujado(
                campo = campo,
                x = ancho * (campo.marco.x0 / tamano.ancho),
                y = alto * (campo.marco.y0 / tamano.alto),
                anchoCampo = ancho * (campo.marco.ancho / tamano.ancho),
                altoCampo = alto * (campo.marco.alto / tamano.alto),
                activo = campo.id == campoActivo,
                alTocar = { alTocarCampo(campo) },
            )
        }
    }
}

@Composable
private fun CampoDibujado(
    campo: CampoFormulario,
    x: Dp,
    y: Dp,
    anchoCampo: Dp,
    altoCampo: Dp,
    activo: Boolean,
    alTocar: () -> Unit,
) {
    // Lo que no se puede rellenar no se resalta. Pintar de ámbar un campo que el
    // emisor bloqueó sería invitar a tocarlo para no dejar escribir después.
    val relleno =
        when {
            !campo.esEditable -> null
            activo -> ColoresPapel.campoActivo
            else -> ColoresPapel.campoPendiente
        }
    val borde = if (activo) ColoresPapel.campoActivoBorde else ColoresPapel.campoPendienteBorde

    Box(
        modifier =
            Modifier
                .offset(x = x, y = y)
                .size(width = anchoCampo, height = altoCampo)
                .then(if (relleno != null) Modifier.background(relleno, FORMA_CAMPO) else Modifier)
                .then(
                    if (campo.esEditable) {
                        Modifier.border(if (activo) GROSOR_ACTIVO else GROSOR_BORDE, borde, FORMA_CAMPO)
                    } else {
                        Modifier
                    },
                ).clickable(enabled = campo.esEditable, onClick = alTocar)
                .testTag(tagCampo(campo.id)),
        contentAlignment = if (campo.tipo.esMarca) Alignment.Center else Alignment.CenterStart,
    ) {
        ContenidoDelCampo(campo)
    }
}

@Composable
private fun ContenidoDelCampo(campo: CampoFormulario) {
    when (campo.tipo) {
        TipoCampo.CASILLA, TipoCampo.RADIO ->
            if (campo.marcado) {
                Box(
                    modifier =
                        Modifier
                            .size(TAMANO_MARCA)
                            .clip(if (campo.tipo == TipoCampo.RADIO) CircleShape else FORMA_CAMPO)
                            .background(ColoresPapel.tinta),
                )
            }

        else ->
            if (campo.valor.isNotEmpty()) {
                Text(
                    text = campo.valor,
                    style = ESTILO_VALOR,
                    maxLines = if (campo.multilinea) MAX_LINEAS_MULTILINEA else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
            }
    }
}

/** Los campos que se dibujan como una marca centrada y no como texto. */
private val TipoCampo.esMarca: Boolean
    get() = this == TipoCampo.CASILLA || this == TipoCampo.RADIO

/**
 * El valor va en tinta y en un tamaño fijo pequeño.
 *
 * Deliberadamente **no** se escala con el zoom ni con la fuente del sistema: esto no
 * es texto de la interfaz, es lo que se leerá dentro del PDF, y la Fase 2 sólo lo
 * enseña. Quien manda sobre su tipografía es la apariencia del campo, y eso lo
 * escribe el motor al guardar.
 */
private val ESTILO_VALOR =
    TextStyle(
        color = ColoresPapel.tinta,
        fontSize = 9.sp,
    )

private val FORMA_CAMPO = RoundedCornerShape(2.dp)
private val GROSOR_BORDE = 1.dp
private val GROSOR_ACTIVO = 1.5.dp
private val TAMANO_MARCA = 8.dp
private const val MAX_LINEAS_MULTILINEA = 3

fun tagCampo(id: IdCampo): String = "campo_${id.pagina}_${id.indice}"
