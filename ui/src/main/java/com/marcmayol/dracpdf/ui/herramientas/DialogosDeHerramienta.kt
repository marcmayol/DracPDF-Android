package com.marcmayol.dracpdf.ui.herramientas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Los rangos que se escriben al dividir: «1-3, 7, 10-12».
 *
 * Se interpreta en base **uno y con los dos extremos dentro**, porque es como los
 * escribe quien los pide: «1-3» son tres páginas. Una página suelta es su propio
 * rango, que ahorra tener que escribir «7-7».
 */
fun rangosDe(
    texto: String,
    paginas: Int,
): List<IntRange>? {
    val trozos = texto.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (trozos.isEmpty()) return null
    return trozos.map { trozo -> rangoDe(trozo, paginas) ?: return null }
}

/** Un trozo suelto: «7» o «10-12». Devuelve `null` si no cabe en el documento. */
private fun rangoDe(
    trozo: String,
    paginas: Int,
): IntRange? {
    val partes = trozo.split('-').map { it.trim() }
    val desde = partes.firstOrNull()?.toIntOrNull()
    // Una página suelta es su propio rango: ahorra tener que escribir «7-7».
    val hasta = if (partes.size == 1) desde else partes.getOrNull(1)?.toIntOrNull()
    if (desde == null || hasta == null) return null
    return if (desde in 1..paginas && hasta in desde..paginas) desde..hasta else null
}

/**
 * Pide los rangos para dividir.
 *
 * El campo enseña qué se espera y **valida mientras se escribe**: un rango imposible
 * —la página 30 de un documento de 6— apaga el botón en vez de dejar pulsar y fallar
 * después.
 */
@Composable
fun DialogoDividir(
    paginas: Int,
    alAceptar: (List<IntRange>) -> Unit,
    alCancelar: () -> Unit,
) {
    var texto by remember { mutableStateOf("1-$paginas") }
    val rangos = rangosDe(texto, paginas)

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Dividir en partes") },
        text = {
            Column {
                Text(
                    text = "Escribe los rangos de páginas, separados por comas. El documento tiene $paginas.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    isError = rangos == null,
                    singleLine = true,
                    placeholder = { Text("1-3, 7, 10-12") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag(TAG_CAMPO_RANGOS),
                )
                if (rangos == null) {
                    Text(
                        text = "Ese rango no cabe en un documento de $paginas páginas.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { rangos?.let(alAceptar) },
                enabled = rangos != null,
                modifier = Modifier.testTag(TAG_ACEPTAR_RANGOS),
            ) { Text("Dividir") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
        modifier = Modifier.testTag(TAG_DIALOGO_DIVIDIR),
    )
}

/**
 * Pide la contraseña para proteger o para quitar la protección.
 *
 * Es el mismo diálogo para las dos cosas porque para el usuario es la misma
 * herramienta —«Proteger»— y lo único que cambia es en qué dirección va.
 */
@Composable
fun DialogoContrasena(
    quitando: Boolean,
    alAceptar: (String) -> Unit,
    alCancelar: () -> Unit,
) {
    var contrasena by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(if (quitando) "Quitar la contraseña" else "Proteger con contraseña") },
        text = {
            Column {
                Text(
                    text =
                        if (quitando) {
                            "Hace falta la contraseña que abre el documento. No hay manera de adivinarla."
                        } else {
                            "Quien abra la copia tendrá que escribirla. Si la pierdes, no se puede recuperar."
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = contrasena,
                    onValueChange = { contrasena = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag(TAG_CAMPO_CLAVE),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { alAceptar(contrasena) },
                // En blanco no protege nada, así que no se deja confirmar.
                enabled = contrasena.isNotBlank(),
                modifier = Modifier.testTag(TAG_ACEPTAR_CLAVE),
            ) { Text(if (quitando) "Quitar" else "Proteger") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
        modifier = Modifier.testTag(TAG_DIALOGO_CLAVE),
    )
}

/**
 * Avisa de lo que se ha visto en los ficheros elegidos antes de operar.
 *
 * Los dos avisos son distintos y por eso se dicen distinto: un firmado **impide** la
 * operación, y uno abierto con cambios sólo advierte de que lo que se va a leer es el
 * fichero del disco y no lo que hay en pantalla a medio rellenar.
 */
@Composable
fun DialogoAvisos(
    firmados: List<String>,
    abiertosConCambios: List<String>,
    alSeguir: () -> Unit,
    alCancelar: () -> Unit,
) {
    val impiden = firmados.isNotEmpty()

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(if (impiden) "No se puede continuar" else "Antes de seguir") },
        text = {
            Column {
                if (impiden) {
                    Text(
                        text =
                            "Estos documentos están firmados y la operación rompería su firma:\n" +
                                firmados.joinToString("\n") { "· $it" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (abiertosConCambios.isNotEmpty()) {
                    Text(
                        text =
                            "Tienes cambios sin guardar en:\n" +
                                abiertosConCambios.joinToString("\n") { "· $it" } +
                                "\n\nSe usará lo que hay en el disco, no lo que ves en pantalla.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = if (impiden) 12.dp else 0.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (!impiden) {
                TextButton(onClick = alSeguir, modifier = Modifier.testTag(TAG_AVISOS_SEGUIR)) { Text("Seguir") }
            }
        },
        dismissButton = {
            TextButton(onClick = alCancelar) { Text(if (impiden) "Entendido" else "Cancelar") }
        },
        modifier = Modifier.testTag(TAG_DIALOGO_AVISOS),
    )
}

const val TAG_DIALOGO_DIVIDIR = "dialogo_dividir"
const val TAG_CAMPO_RANGOS = "dividir_campo_rangos"
const val TAG_ACEPTAR_RANGOS = "dividir_aceptar"

const val TAG_DIALOGO_CLAVE = "dialogo_contrasena"
const val TAG_CAMPO_CLAVE = "contrasena_campo"
const val TAG_ACEPTAR_CLAVE = "contrasena_aceptar"

const val TAG_DIALOGO_AVISOS = "dialogo_avisos"
const val TAG_AVISOS_SEGUIR = "avisos_seguir"
