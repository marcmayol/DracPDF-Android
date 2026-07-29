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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.FormatoTexto
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
    alEscribir: (CampoFormulario, String) -> Unit = { _, _ -> },
    hayCampoSiguiente: Boolean = false,
    alIrAlSiguiente: () -> Unit = {},
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
                alEscribir = { valor -> alEscribir(campo, valor) },
                haySiguiente = hayCampoSiguiente,
                alIrAlSiguiente = alIrAlSiguiente,
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
    alEscribir: (String) -> Unit,
    haySiguiente: Boolean,
    alIrAlSiguiente: () -> Unit,
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
        if (campo.tipo == TipoCampo.TEXTO && activo && campo.esEditable) {
            EditorDeTexto(
                campo = campo,
                haySiguiente = haySiguiente,
                alEscribir = alEscribir,
                alIrAlSiguiente = alIrAlSiguiente,
            )
        } else {
            ContenidoDelCampo(campo)
        }
    }
}

/**
 * El campo de texto activo, editable en su sitio.
 *
 * **Se escribe en el documento al perder el foco**, no en cada tecla. Cada escritura
 * hace que el motor regenere la apariencia del campo y vuelva a rasterizar la
 * página; hacerlo por pulsación convertiría teclear en un tirón continuo. Lo que se
 * ve mientras tanto es un borrador de pantalla, y en el momento en que el dedo se va
 * a otro sitio pasa a ser el valor del documento.
 *
 * El borrador se reinicia cuando cambia el valor que trae el campo, para que el
 * texto que devuelve el motor —recortado por `MaxLen`, o reformateado— sea el que
 * quede a la vista.
 */
@Composable
private fun EditorDeTexto(
    campo: CampoFormulario,
    haySiguiente: Boolean,
    alEscribir: (String) -> Unit,
    alIrAlSiguiente: () -> Unit,
) {
    var borrador by remember(campo.id, campo.valor) { mutableStateOf(campo.valor) }
    val foco = remember { FocusRequester() }
    val teclado = LocalSoftwareKeyboardController.current

    LaunchedEffect(campo.id) { foco.requestFocus() }

    // Al girar el teléfono, Compose desmonta esto y lo vuelve a montar. Volcar el
    // borrador al documento en ese momento es lo que hace que no se pierda nada: el
    // documento es la única fuente de verdad y sobrevive a la rotación, mientras que
    // cualquier cosa que se quedara aquí, no. Vale igual para salir del modo o para
    // que la página se vaya de pantalla.
    val ultimoBorrador by rememberUpdatedState(borrador)
    val valorDelCampo by rememberUpdatedState(campo.valor)
    DisposableEffect(campo.id) {
        onDispose { if (ultimoBorrador != valorDelCampo) alEscribir(ultimoBorrador) }
    }

    BasicTextField(
        value = borrador,
        onValueChange = { escrito ->
            // El tope de caracteres es del documento y se respeta aquí también: dejar
            // teclear de más para recortar al guardar sería enseñar algo que se pierde.
            borrador = campo.maxLongitud?.let { escrito.take(it) } ?: escrito
        },
        textStyle = ESTILO_VALOR,
        singleLine = !campo.multilinea,
        cursorBrush = SolidColor(ColoresPapel.tinta),
        visualTransformation =
            if (campo.esContrasena) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = tecladoPara(campo),
                imeAction = accionPara(campo, haySiguiente),
            ),
        keyboardActions =
            KeyboardActions(
                // «Siguiente» del teclado y «siguiente» de la barra son la misma cosa
                // y llevan al mismo campo: dos caminos, un solo orden.
                onNext = { alIrAlSiguiente() },
                onDone = { teclado?.hide() },
            ),
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp)
                .focusRequester(foco)
                .onFocusChanged { estado -> if (!estado.isFocused && borrador != campo.valor) alEscribir(borrador) }
                .testTag(tagEditor(campo.id)),
    )
}

/**
 * El teclado que toca según lo que el PDF dice que se escribe en el campo.
 *
 * Sale del formato declarado y no de adivinar por el nombre: un campo de importe
 * pide dígitos, uno de teléfono pide su teclado, y una fecha necesita las barras, así
 * que se queda en el de texto aunque sea «numérica». Un teclado numérico sin
 * separadores obligaría a cambiar de teclado a mitad de fecha.
 */
private fun tecladoPara(campo: CampoFormulario): KeyboardType =
    when {
        campo.esContrasena -> KeyboardType.Password
        campo.formatoTexto == FormatoTexto.NUMERO -> KeyboardType.Number
        campo.formatoTexto == FormatoTexto.ESPECIAL -> KeyboardType.Phone
        else -> KeyboardType.Text
    }

/**
 * Un campo multilínea necesita su tecla de salto de línea; el resto ofrece
 * «siguiente» si hay adónde ir, y «hecho» si es el último.
 */
private fun accionPara(
    campo: CampoFormulario,
    haySiguiente: Boolean,
): ImeAction =
    when {
        campo.multilinea -> ImeAction.Default
        haySiguiente -> ImeAction.Next
        else -> ImeAction.Done
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

fun tagEditor(id: IdCampo): String = "editor_${id.pagina}_${id.indice}"
