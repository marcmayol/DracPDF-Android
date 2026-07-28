package com.marcmayol.dracpdf.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Tema de la aplicación.
 *
 * No hay `dynamicDarkColorScheme()` ni lo habrá: con color dinámico el acento podría
 * acabar a un paso del verde de «firma válida», y el ámbar de «estado desconocido»
 * perdería su lectura. En esta aplicación el color transporta significado legal.
 */
@Composable
fun TemaDracPDF(
    preferencia: PreferenciaTema = PreferenciaTema.SISTEMA,
    contenido: @Composable () -> Unit,
) {
    val oscuro =
        when (preferencia) {
            PreferenciaTema.CLARO -> false
            PreferenciaTema.OSCURO -> true
            PreferenciaTema.SISTEMA -> isSystemInDarkTheme()
        }

    CompositionLocalProvider(
        LocalColoresLadon provides if (oscuro) ColoresLadonOscuro else ColoresLadonClaro,
    ) {
        MaterialTheme(
            colorScheme = if (oscuro) EsquemaOscuroLadon else EsquemaClaroLadon,
            typography = TipografiaLadon,
            shapes = FormasLadon,
            content = contenido,
        )
    }
}
