package com.marcmayol.dracpdf.ui.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.ui.tema.FormasLadon
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * La contraseña de un PDF cifrado.
 *
 * Va en hoja inferior y no en diálogo, siguiendo la de «Firmar con certificado» del
 * diseño, que es la otra pantalla de la aplicación que pide una contraseña: mismo
 * problema, misma forma. El error se enseña bajo el campo y no en un aviso flotante,
 * porque quien se equivoca al escribir necesita el campo delante para corregir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaContrasena(
    nombreDocumento: String,
    huboError: Boolean,
    alAceptar: (String) -> Unit,
    alCancelar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var contrasena by remember { mutableStateOf("") }
    val estadoHoja = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = alCancelar,
        sheetState = estadoHoja,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.testTag(TAG_HOJA_CONTRASENA),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Documento protegido",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "«$nombreDocumento» pide una contraseña para abrirse.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = contrasena,
                onValueChange = { contrasena = it },
                label = { Text("Contraseña del documento") },
                singleLine = true,
                isError = huboError,
                shape = FormasLadon.small,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { if (contrasena.isNotEmpty()) alAceptar(contrasena) }),
                modifier = Modifier.fillMaxWidth().testTag(TAG_CAMPO_CONTRASENA),
            )

            if (huboError) {
                Text(
                    text = "La contraseña no abre el documento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(TAG_ERROR_CONTRASENA),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(MedidasLadon.hueco, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = alCancelar,
                    modifier = Modifier.heightIn(min = MedidasLadon.areaTactil).testTag(TAG_CANCELAR_CONTRASENA),
                ) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = { alAceptar(contrasena) },
                    // Sin contraseña no hay nada que probar: el botón espera.
                    enabled = contrasena.isNotEmpty(),
                    shape = FormasLadon.large,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    modifier = Modifier.heightIn(min = MedidasLadon.areaTactil).testTag(TAG_ACEPTAR_CONTRASENA),
                ) {
                    Text("Abrir", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

const val TAG_HOJA_CONTRASENA = "hoja_contrasena"
const val TAG_CAMPO_CONTRASENA = "contrasena_campo"
const val TAG_ACEPTAR_CONTRASENA = "contrasena_aceptar"
const val TAG_CANCELAR_CONTRASENA = "contrasena_cancelar"
const val TAG_ERROR_CONTRASENA = "contrasena_error"
