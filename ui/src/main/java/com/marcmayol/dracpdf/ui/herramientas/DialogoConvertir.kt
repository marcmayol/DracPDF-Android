package com.marcmayol.dracpdf.ui.herramientas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.dominio.puertos.AjustesImagen
import com.marcmayol.dracpdf.dominio.puertos.FormatoImagen
import com.marcmayol.dracpdf.ui.componentes.ChipLadon
import kotlin.math.roundToInt

/**
 * Adónde va el documento: a texto o a imágenes.
 *
 * **Una sola entrada de conversión**, como manda el plan. Aquí no se elige entre
 * «exportar» y «convertir» —que para el usuario son la misma cosa— sino entre los
 * destinos de verdad, que sí son distintos: un `.txt` es un fichero y las imágenes son
 * una por página, y por eso lo primero pide dónde guardar y lo segundo, en qué carpeta.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoConvertir(
    paginas: Int,
    alElegirTexto: () -> Unit,
    alElegirImagenes: (List<Int>, AjustesImagen) -> Unit,
    alCancelar: () -> Unit,
    alElegirDocumento: (DestinoDeConversion) -> Unit = {},
    alElegirTablas: (DestinoDeTabla) -> Unit = {},
) {
    var destino by remember { mutableStateOf(DestinoDeConversion.TEXTO) }
    var formato by remember { mutableStateOf(FormatoImagen.PNG) }
    var tabla by remember { mutableStateOf(DestinoDeTabla.CSV) }
    var calidad by remember { mutableFloatStateOf(AjustesImagen.CALIDAD_POR_DEFECTO.toFloat()) }
    var escala by remember { mutableFloatStateOf(1f) }
    var texto by remember { mutableStateOf("1-$paginas") }

    val rangos = rangosDe(texto, paginas)
    val puedeConvertir = destino != DestinoDeConversion.IMAGENES || rangos != null

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Convertir a") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Una rejilla de chips y no una lista de radios: son ocho destinos
                // cortos, y en un móvil ocho filas serían casi una pantalla entera de
                // desplazamiento para elegir una palabra.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DestinoDeConversion.entries.forEach { candidato ->
                        ChipLadon(
                            texto = candidato.etiqueta,
                            elegido = destino == candidato,
                            alPulsar = { destino = candidato },
                            tag = candidato.tag,
                        )
                    }
                }

                Text(
                    text = destino.explicacion,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp).testTag(TAG_CONVERTIR_EXPLICACION),
                )

                when (destino) {
                    DestinoDeConversion.IMAGENES ->
                        AjustesDeImagen(
                            paginas = paginas,
                            formato = formato,
                            calidad = calidad,
                            escala = escala,
                            texto = texto,
                            rangoValido = rangos != null,
                            alElegirFormato = { formato = it },
                            alFijarCalidad = { calidad = it },
                            alFijarEscala = { escala = it },
                            alEscribirRangos = { texto = it },
                        )

                    DestinoDeConversion.TABLAS ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            DestinoDeTabla.entries.forEach { candidata ->
                                ChipLadon(
                                    texto = candidata.etiqueta,
                                    elegido = tabla == candidata,
                                    alPulsar = { tabla = candidata },
                                    tag = candidata.tag,
                                )
                            }
                        }

                    else -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (destino) {
                        DestinoDeConversion.TEXTO -> alElegirTexto()
                        // El dominio cuenta desde cero y el usuario desde uno: la resta
                        // se hace aquí, en el único sitio que sabe de las dos cuentas.
                        DestinoDeConversion.IMAGENES ->
                            alElegirImagenes(
                                rangos.orEmpty().flatMap { rango -> rango.map { it - 1 } }.distinct(),
                                AjustesImagen(formato = formato, escala = escala, calidad = calidad.roundToInt()),
                            )

                        DestinoDeConversion.TABLAS -> alElegirTablas(tabla)
                        else -> alElegirDocumento(destino)
                    }
                },
                enabled = puedeConvertir,
                modifier = Modifier.testTag(TAG_CONVERTIR_ACEPTAR),
            ) { Text("Convertir") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
        modifier = Modifier.testTag(TAG_DIALOGO_CONVERTIR),
    )
}

@Composable
private fun AjustesDeImagen(
    paginas: Int,
    formato: FormatoImagen,
    calidad: Float,
    escala: Float,
    texto: String,
    rangoValido: Boolean,
    alElegirFormato: (FormatoImagen) -> Unit,
    alFijarCalidad: (Float) -> Unit,
    alFijarEscala: (Float) -> Unit,
    alEscribirRangos: (String) -> Unit,
) {
    // Sin explicación propia: la de arriba ya dice lo que va a pasar, y esta repetía la
    // misma frase palabra por palabra dos párrafos seguidos. Sólo se veía en pantalla.
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            FormatoImagen.entries.forEach { candidato ->
                ChipLadon(
                    texto = candidato.name,
                    elegido = formato == candidato,
                    alPulsar = { alElegirFormato(candidato) },
                    tag = tagFormato(candidato),
                )
            }
        }

        // La calidad sólo significa algo donde hay pérdida. En PNG no se enseña, en vez
        // de enseñar un mando que no mueve nada.
        if (formato != FormatoImagen.PNG) {
            Text(
                text = "Calidad: ${calidad.roundToInt()} %",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Slider(
                value = calidad,
                onValueChange = alFijarCalidad,
                valueRange = 1f..AjustesImagen.CALIDAD_MAXIMA.toFloat(),
                modifier = Modifier.testTag(TAG_CONVERTIR_CALIDAD),
            )
        }

        Text(
            text = "Tamaño: ${escala.roundToInt()}× el de la página",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ESCALAS.forEach { candidata ->
                ChipLadon(
                    texto = "${candidata.roundToInt()}×",
                    elegido = escala == candidata,
                    alPulsar = { alFijarEscala(candidata) },
                    tag = tagEscala(candidata),
                )
            }
        }

        OutlinedTextField(
            value = texto,
            onValueChange = alEscribirRangos,
            isError = !rangoValido,
            singleLine = true,
            label = { Text("Páginas") },
            placeholder = { Text("1-3, 7") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag(TAG_CONVERTIR_PAGINAS),
        )
        if (!rangoValido) {
            Text(
                text = "Ese rango no cabe en un documento de $paginas páginas.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private val ESCALAS = listOf(1f, 2f, 3f)

const val TAG_DIALOGO_CONVERTIR = "dialogo_convertir"
const val TAG_CONVERTIR_TEXTO = "convertir_a_texto"
const val TAG_CONVERTIR_EXPLICACION = "convertir_explicacion"
const val TAG_CONVERTIR_IMAGENES = "convertir_a_imagenes"
const val TAG_CONVERTIR_CALIDAD = "convertir_calidad"
const val TAG_CONVERTIR_PAGINAS = "convertir_paginas"
const val TAG_CONVERTIR_ACEPTAR = "convertir_aceptar"

fun tagFormato(formato: FormatoImagen): String = "convertir_formato_${formato.name.lowercase()}"

fun tagEscala(escala: Float): String = "convertir_escala_${escala.roundToInt()}"
