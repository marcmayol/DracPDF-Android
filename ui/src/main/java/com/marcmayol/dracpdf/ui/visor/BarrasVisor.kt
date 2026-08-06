package com.marcmayol.dracpdf.ui.visor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
    documentosAbiertos: Int = 1,
    alAbrirDocumentos: () -> Unit = {},
    alAbrirOtro: () -> Unit = {},
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
        // El nombre del archivo es la puerta a los documentos abiertos, y el chevron
        // lo delata: unas pestañas serían chrome permanente, que es justo lo que esta
        // aplicación no hace. Con un solo documento no hay nada que elegir, así que
        // no se anuncia.
        val hayVarios = documentosAbiertos > 1
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = MedidasLadon.areaTactil)
                    .clickable(enabled = hayVarios, onClick = alAbrirDocumentos)
                    .padding(horizontal = 4.dp)
                    .testTag(TAG_TITULO),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = nombre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (hayVarios) {
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TAG_CHEVRON_DOCUMENTOS),
                )
            }
        }
        BotonIconoLadon(
            icono = IconosLadon.buscar,
            descripcion = "Buscar en el documento",
            alPulsar = {},
            // Llega en la Fase 7. Se ve y no se pulsa: una acción que desaparece deja
            // al usuario buscándola, y el inventario exige poder comprobar su estado.
            habilitado = false,
            modifier = Modifier.testTag(TAG_BUSCAR),
        )
        Box {
            var menuAbierto by remember { mutableStateOf(false) }
            BotonIconoLadon(
                icono = IconosLadon.mas,
                descripcion = "Más acciones",
                alPulsar = { menuAbierto = true },
                modifier = Modifier.testTag(TAG_MENU),
            )
            MenuDelVisor(
                abierto = menuAbierto,
                alCerrar = { menuAbierto = false },
                alAbrirDocumentos = {
                    menuAbierto = false
                    alAbrirDocumentos()
                },
                alAbrirOtro = {
                    menuAbierto = false
                    alAbrirOtro()
                },
            )
        }
    }
}

/**
 * El ⋮ del visor: lo que en el escritorio era el menú Archivo.
 *
 * «Documentos abiertos» vive aquí y no como botón suelto de la barra, que es donde
 * estuvo hasta la revisión de conformidad: el diseño reserva los cuatro sitios de la
 * barra para atrás · nombre · buscar · ⋮, y la lista de abiertos tiene su entrada
 * principal en el propio nombre del archivo, con el chevron que lo delata.
 *
 * El resto son las acciones de documento de la §15. Se ven apagadas hasta que su fase
 * las traiga: un menú que cambia de largo entre versiones obliga a volver a buscarlo
 * todo cada vez.
 */
@Composable
private fun MenuDelVisor(
    abierto: Boolean,
    alCerrar: () -> Unit,
    alAbrirDocumentos: () -> Unit,
    alAbrirOtro: () -> Unit,
) {
    DropdownMenu(
        expanded = abierto,
        onDismissRequest = alCerrar,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        EntradaDeMenu(
            etiqueta = "Documentos abiertos",
            icono = IconosLadon.documentos,
            alPulsar = alAbrirDocumentos,
            tag = TAG_MENU_DOCUMENTOS,
        )
        EntradaDeMenu(
            etiqueta = "Abrir otro PDF",
            icono = IconosLadon.abrir,
            alPulsar = alAbrirOtro,
            tag = TAG_MENU_ABRIR,
        )
        EntradaDeMenu("Guardar una copia", IconosLadon.guardar, tag = TAG_MENU_COPIA)
        EntradaDeMenu("Imprimir", IconosLadon.imprimir, tag = TAG_MENU_IMPRIMIR)
        EntradaDeMenu("Compartir", IconosLadon.compartir, tag = TAG_MENU_COMPARTIR)
        EntradaDeMenu("Propiedades", IconosLadon.propiedades, tag = TAG_MENU_PROPIEDADES)
    }
}

@Composable
private fun EntradaDeMenu(
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

/**
 * La barra superior de un modo, que **sustituye** a la del visor mientras el modo
 * dura: ✕ a la izquierda, el nombre del modo, y su acción de confirmación a la
 * derecha.
 *
 * La regla del diseño es que nunca conviven dos barras superiores, y tiene su razón:
 * con la del documento todavía puesta, «atrás» y «✕» aparecen a la vez —dos maneras
 * de salir de dos cosas distintas, a un centímetro la una de la otra— y la de arriba
 * invita a cerrar el documento cuando lo que se quería era cerrar el modo.
 */
@Composable
fun BarraSuperiorDelModo(
    titulo: String,
    accion: String,
    alCerrar: () -> Unit,
    alAccionar: () -> Unit,
    modifier: Modifier = Modifier,
    accionHabilitada: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .statusBarsPadding()
                .heightIn(min = MedidasLadon.barraSuperior)
                .padding(horizontal = 6.dp)
                .testTag(TAG_BARRA_MODO),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotonIconoLadon(
            icono = IconosLadon.cerrar,
            descripcion = "Salir del modo $titulo",
            alPulsar = alCerrar,
            modifier = Modifier.testTag(TAG_MODO_CERRAR),
        )
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp).testTag(TAG_MODO_TITULO),
        )
        Text(
            text = accion,
            style = MaterialTheme.typography.labelLarge,
            // Deshabilitada se apaga, no desaparece: que «Guardar» esté ahí gris es lo
            // que dice que no queda nada por guardar.
            color =
                if (accionHabilitada) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = ALFA_DESHABILITADO)
                },
            modifier =
                Modifier
                    .heightIn(min = MedidasLadon.areaTactil)
                    .clickable(enabled = accionHabilitada, onClick = alAccionar)
                    .padding(horizontal = 12.dp, vertical = 14.dp)
                    .semantics { role = Role.Button }
                    .testTag(TAG_MODO_ACCION),
        )
    }
}

/**
 * La barra inferior que toca según el modo. **Un modo, una barra**: es este `when`,
 * y no hay otro sitio donde se decida.
 *
 * De aquí sale gratis una garantía que el escritorio tuvo que aprender a base de
 * bugs: los controles de un modo no pueden verse fuera de él, porque fuera de él ni
 * siquiera se componen.
 */
@Composable
fun BarraDelModo(
    modo: ModoVisor,
    alAbrirIndice: () -> Unit,
    alEntrarEnFormulario: () -> Unit,
    alCampoAnterior: () -> Unit,
    alCampoSiguiente: () -> Unit,
    alConfirmarColocacion: () -> Unit,
    alCancelarColocacion: () -> Unit,
    alAbrirFirmas: () -> Unit,
    campos: Int,
    modifier: Modifier = Modifier,
    formularioDisponible: Boolean = false,
    cambiosSinGuardar: Boolean = false,
    guardando: Boolean = false,
    hayCampoAnterior: Boolean = false,
    hayCampoSiguiente: Boolean = false,
) {
    when (modo) {
        ModoVisor.Lectura ->
            BarraInferiorVisor(
                alAbrirIndice = alAbrirIndice,
                alAbrirFormulario = alEntrarEnFormulario,
                alAbrirFirmas = alAbrirFirmas,
                formularioDisponible = formularioDisponible,
                modifier = modifier,
            )

        ModoVisor.ColocarFirma ->
            BarraColocacion(
                alConfirmar = alConfirmarColocacion,
                alCancelar = alCancelarColocacion,
                modifier = modifier,
            )

        ModoVisor.Formulario ->
            BarraFormulario(
                campos = campos,
                alCampoAnterior = alCampoAnterior,
                alCampoSiguiente = alCampoSiguiente,
                cambiosSinGuardar = cambiosSinGuardar,
                guardando = guardando,
                hayCampoAnterior = hayCampoAnterior,
                hayCampoSiguiente = hayCampoSiguiente,
                modifier = modifier,
            )
    }
}

/**
 * Barra inferior de lectura, 80 dp, cuatro acciones: Índice · Formulario ·
 * Herramientas · Firmas. Son las cuatro cosas que se abren a mitad de lectura, y el
 * pulgar llega a todas. Es una barra de acciones, no de navegación: no hay secciones
 * entre las que moverse, se abren hojas.
 */
@Composable
fun BarraInferiorVisor(
    alAbrirIndice: () -> Unit,
    modifier: Modifier = Modifier,
    alAbrirFormulario: () -> Unit = {},
    alAbrirFirmas: () -> Unit = {},
    formularioDisponible: Boolean = false,
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
            // Se ve siempre y se pulsa sólo cuando hay algo que rellenar: en un PDF sin
            // campos, o en un XFA que no se puede rellenar, entrar al modo llevaría a
            // una pantalla sin nada dentro.
            habilitado = formularioDisponible,
            alPulsar = alAbrirFormulario,
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
            icono = IconosLadon.firmaDibujada,
            etiqueta = "Firmas",
            tag = TAG_DESTINO_FIRMAS,
            habilitado = true,
            alPulsar = alAbrirFirmas,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * La barra inferior del modo de formulario: por qué campo se va, y cómo ir al
 * anterior y al siguiente.
 *
 * Salir y guardar **no** están aquí: son la ✕ y la acción de la barra de arriba,
 * donde el diseño pone la confirmación de todo modo. Abajo queda lo que se usa
 * mientras se rellena, que es justo lo que el pulgar alcanza sin soltar el teléfono.
 */
@Composable
fun BarraFormulario(
    campos: Int,
    modifier: Modifier = Modifier,
    alCampoAnterior: () -> Unit = {},
    alCampoSiguiente: () -> Unit = {},
    cambiosSinGuardar: Boolean = false,
    guardando: Boolean = false,
    hayCampoAnterior: Boolean = false,
    hayCampoSiguiente: Boolean = false,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .navigationBarsPadding()
                .heightIn(min = MedidasLadon.barraInferior)
                .padding(horizontal = 6.dp)
                .testTag(TAG_BARRA_FORMULARIO),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // El estado sin guardar se dice con palabras y no con un punto de color:
            // «sin guardar» lo entiende cualquiera, un punto naranja no.
            text =
                when {
                    guardando -> "Guardando…"
                    cambiosSinGuardar -> "Sin guardar"
                    campos == 1 -> "1 campo"
                    else -> "$campos campos"
                },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp).testTag(TAG_FORM_CONTADOR),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonIconoLadon(
                icono = IconosLadon.paginaAnterior,
                descripcion = "Campo anterior",
                alPulsar = alCampoAnterior,
                habilitado = hayCampoAnterior,
                modifier = Modifier.testTag(TAG_FORM_ANTERIOR),
            )
            BotonIconoLadon(
                icono = IconosLadon.paginaSiguiente,
                descripcion = "Campo siguiente",
                alPulsar = alCampoSiguiente,
                habilitado = hayCampoSiguiente,
                modifier = Modifier.testTag(TAG_FORM_SIGUIENTE),
            )
        }
    }
}

/**
 * La barra de colocar una firma: cancelar y confirmar, nada más.
 *
 * Los dos controles están **siempre visibles mientras dura el modo**, que es la
 * lección que el escritorio pagó cara: unos controles de colocación que aparecen y
 * desaparecen dejan al usuario sin saber si sigue colocando algo o ya lo ha soltado.
 * Y fuera del modo no existen, porque la barra sale del `when` de arriba.
 */
@Composable
fun BarraColocacion(
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .navigationBarsPadding()
                .heightIn(min = MedidasLadon.barraInferior)
                .padding(horizontal = 12.dp)
                .testTag(TAG_BARRA_COLOCACION),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Cancelar",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .heightIn(min = MedidasLadon.areaTactil)
                    .clickable(onClick = alCancelar)
                    .padding(horizontal = 12.dp, vertical = 14.dp)
                    .testTag(TAG_COLOCACION_CANCELAR),
        )
        Text(
            text = "Arrastra la firma y ajústala",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp),
        )
        Text(
            text = "Colocar",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .heightIn(min = MedidasLadon.areaTactil)
                    .clickable(onClick = alConfirmar)
                    .padding(horizontal = 12.dp, vertical = 14.dp)
                    .testTag(TAG_COLOCACION_CONFIRMAR),
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

/** El ⋮ del visor y lo que despliega. */
const val TAG_MENU = "visor_menu"
const val TAG_MENU_DOCUMENTOS = "visor_menu_documentos"
const val TAG_MENU_ABRIR = "visor_menu_abrir"
const val TAG_MENU_COPIA = "visor_menu_copia"
const val TAG_MENU_IMPRIMIR = "visor_menu_imprimir"
const val TAG_MENU_COMPARTIR = "visor_menu_compartir"
const val TAG_MENU_PROPIEDADES = "visor_menu_propiedades"
const val TAG_DESTINO_INDICE = "visor_destino_indice"
const val TAG_DESTINO_FORMULARIO = "visor_destino_formulario"
const val TAG_DESTINO_HERRAMIENTAS = "visor_destino_herramientas"
const val TAG_DESTINO_FIRMAS = "visor_destino_firmas"
const val TAG_CHEVRON_DOCUMENTOS = "visor_chevron_documentos"

const val TAG_BARRA_FORMULARIO = "visor_barra_formulario"
const val TAG_FORM_CONTADOR = "visor_form_contador"
const val TAG_FORM_ANTERIOR = "visor_form_anterior"
const val TAG_FORM_SIGUIENTE = "visor_form_siguiente"

/** La barra superior de un modo: la ✕, el título y la acción de confirmación. */
const val TAG_BARRA_MODO = "visor_barra_modo"
const val TAG_MODO_CERRAR = "visor_modo_cerrar"
const val TAG_MODO_TITULO = "visor_modo_titulo"
const val TAG_MODO_ACCION = "visor_modo_accion"

const val TAG_BARRA_COLOCACION = "visor_barra_colocacion"
const val TAG_COLOCACION_CANCELAR = "visor_colocacion_cancelar"
const val TAG_COLOCACION_CONFIRMAR = "visor_colocacion_confirmar"
