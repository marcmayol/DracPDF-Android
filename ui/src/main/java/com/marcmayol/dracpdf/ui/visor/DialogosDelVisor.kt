package com.marcmayol.dracpdf.ui.visor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.dominio.modelo.PropiedadesDocumento

/**
 * Antes de salir a un enlace del documento.
 *
 * **Se pregunta siempre.** El enlace de un PDF lo escribió quien mandó el documento, y
 * salir del visor a un navegador con lo que ponga ahí no puede pasar por rozar la
 * pantalla sin querer. Se enseña la dirección entera —no acortada— porque es lo único
 * con lo que el usuario puede decidir.
 */
@Composable
fun DialogoEnlace(
    url: String,
    alAbrir: () -> Unit,
    alCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Salir a este enlace") },
        text = {
            Column {
                Text(
                    text = "El documento lleva a:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp).testTag(TAG_ENLACE_URL),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = alAbrir, modifier = Modifier.testTag(TAG_ENLACE_ABRIR)) { Text("Abrir") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
        modifier = Modifier.testTag(TAG_DIALOGO_ENLACE),
    )
}

/**
 * La ficha del documento.
 *
 * Enseña **sólo lo que el PDF dice de sí mismo**: lo que no trae no se rellena con
 * suposiciones. Un documento sin título no tiene por título su nombre de fichero; eso
 * sería inventarse un dato en una pantalla cuyo único trabajo es no inventarse
 * ninguno.
 */
@Composable
fun DialogoPropiedades(
    propiedades: PropiedadesDocumento,
    alCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Propiedades") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Dato("Título", propiedades.titulo)
                Dato("Autor", propiedades.autor)
                Dato("Asunto", propiedades.asunto)
                Dato("Palabras clave", propiedades.palabrasClave)
                Dato("Creado con", propiedades.creador)
                Dato("Producido por", propiedades.productor)
                Dato("Creado", propiedades.creado)
                Dato("Modificado", propiedades.modificado)
                Dato("Formato", propiedades.formato)
                Dato("Páginas", "${propiedades.paginas}")
                Dato("Tamaño", propiedades.bytes?.let(::enTamanoLegible))
                Dato("Protegido", if (propiedades.cifrado) "Sí, con contraseña" else "No")
            }
        },
        confirmButton = {
            TextButton(onClick = alCerrar, modifier = Modifier.testTag(TAG_PROPIEDADES_CERRAR)) { Text("Cerrar") }
        },
        modifier = Modifier.testTag(TAG_DIALOGO_PROPIEDADES),
    )
}

/** Una fila de la ficha. Lo que no está, no se enseña: ni la etiqueta. */
@Composable
private fun Dato(
    etiqueta: String,
    valor: String?,
) {
    if (valor.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(ANCHO_ETIQUETA),
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(ANCHO_VALOR),
        )
    }
}

/** Bytes en algo que se pueda leer de un vistazo. */
private fun enTamanoLegible(bytes: Long): String =
    when {
        bytes >= UN_MEGA -> "%.1f MB".format(bytes.toFloat() / UN_MEGA)
        bytes >= UN_KILO -> "${bytes / UN_KILO} KB"
        else -> "$bytes bytes"
    }

private const val UN_KILO = 1024L
private const val UN_MEGA = UN_KILO * UN_KILO
private const val ANCHO_ETIQUETA = 0.42f
private const val ANCHO_VALOR = 0.58f

const val TAG_DIALOGO_ENLACE = "dialogo_enlace"
const val TAG_ENLACE_URL = "enlace_url"
const val TAG_ENLACE_ABRIR = "enlace_abrir"

const val TAG_DIALOGO_PROPIEDADES = "dialogo_propiedades"
const val TAG_PROPIEDADES_CERRAR = "propiedades_cerrar"
