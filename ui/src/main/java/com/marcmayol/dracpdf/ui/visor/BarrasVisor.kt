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
    documentosAbiertos: Int = 1,
    alAbrirDocumentos: () -> Unit = {},
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
        BotonIconoLadon(
            icono = IconosLadon.documentos,
            descripcion = "Documentos abiertos",
            alPulsar = alAbrirDocumentos,
            modifier = Modifier.testTag(TAG_MENU),
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
    alSalirDelFormulario: () -> Unit,
    alGuardar: () -> Unit,
    campos: Int,
    modifier: Modifier = Modifier,
    formularioDisponible: Boolean = false,
    cambiosSinGuardar: Boolean = false,
    guardando: Boolean = false,
) {
    when (modo) {
        ModoVisor.Lectura ->
            BarraInferiorVisor(
                alAbrirIndice = alAbrirIndice,
                alAbrirFormulario = alEntrarEnFormulario,
                formularioDisponible = formularioDisponible,
                modifier = modifier,
            )

        ModoVisor.Formulario ->
            BarraFormulario(
                campos = campos,
                alSalir = alSalirDelFormulario,
                alGuardar = alGuardar,
                cambiosSinGuardar = cambiosSinGuardar,
                guardando = guardando,
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
            icono = IconosLadon.firmaCertificado,
            etiqueta = "Firmas",
            tag = TAG_DESTINO_FIRMAS,
            habilitado = false,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * La barra del modo de formulario: cuántos campos hay, ir al anterior y al
 * siguiente, y salir.
 *
 * Existe **sólo** dentro del modo, y de ahí que «Hecho» no pueda aparecer donde no
 * hay nada que dar por hecho. La navegación entre campos llega con el foco, en la
 * tarea 4 de esta fase; hasta entonces se ve y no se pulsa, como el resto de lo que
 * aún no está.
 */
@Composable
fun BarraFormulario(
    campos: Int,
    alSalir: () -> Unit,
    modifier: Modifier = Modifier,
    alGuardar: () -> Unit = {},
    cambiosSinGuardar: Boolean = false,
    guardando: Boolean = false,
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
                icono = IconosLadon.guardar,
                descripcion = "Guardar los cambios",
                alPulsar = alGuardar,
                // Sin cambios no hay nada que guardar, y guardar dos veces lo mismo
                // dejaría una revisión vacía en el fichero.
                habilitado = cambiosSinGuardar && !guardando,
                modifier = Modifier.testTag(TAG_FORM_GUARDAR),
            )
            BotonIconoLadon(
                icono = IconosLadon.paginaAnterior,
                descripcion = "Campo anterior",
                alPulsar = {},
                habilitado = false,
                modifier = Modifier.testTag(TAG_FORM_ANTERIOR),
            )
            BotonIconoLadon(
                icono = IconosLadon.paginaSiguiente,
                descripcion = "Campo siguiente",
                alPulsar = {},
                habilitado = false,
                modifier = Modifier.testTag(TAG_FORM_SIGUIENTE),
            )
            Row(
                modifier =
                    Modifier
                        .heightIn(min = MedidasLadon.areaTactil)
                        .clickable(onClick = alSalir)
                        .padding(horizontal = 12.dp)
                        .testTag(TAG_FORM_HECHO),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Hecho",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
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
const val TAG_CHEVRON_DOCUMENTOS = "visor_chevron_documentos"

const val TAG_BARRA_FORMULARIO = "visor_barra_formulario"
const val TAG_FORM_CONTADOR = "visor_form_contador"
const val TAG_FORM_ANTERIOR = "visor_form_anterior"
const val TAG_FORM_SIGUIENTE = "visor_form_siguiente"
const val TAG_FORM_HECHO = "visor_form_hecho"
const val TAG_FORM_GUARDAR = "visor_form_guardar"
