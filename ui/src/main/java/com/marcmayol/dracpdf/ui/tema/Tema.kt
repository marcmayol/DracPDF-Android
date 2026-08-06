package com.marcmayol.dracpdf.ui.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Si el tema en pie es el oscuro.
 *
 * Existe porque hay cosas que no son un color y aun así dependen del tema —la silueta
 * del dragón, que se dibuja en tinta o en blanco y nunca a medias—. Sin esto cada
 * pantalla volvería a preguntarle al sistema por su cuenta y contestaría lo contrario
 * que el tema cuando el usuario ha elegido a mano.
 */
val LocalTemaOscuro = staticCompositionLocalOf { true }

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
        LocalTemaOscuro provides oscuro,
    ) {
        MaterialTheme(
            colorScheme = if (oscuro) EsquemaOscuroLadon else EsquemaClaroLadon,
            typography = TipografiaLadon,
            shapes = FormasLadon,
            content = contenido,
        )
    }
}
