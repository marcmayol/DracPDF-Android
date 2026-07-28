package com.dracpdf.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LadonDark = darkColorScheme(
    primary            = LadonDarkPrimary,
    onPrimary          = LadonDarkOnPrimary,
    primaryContainer   = LadonDarkPrimaryContainer,
    onPrimaryContainer = LadonDarkOnSurface,
    background         = LadonDarkBackground,
    onBackground       = LadonDarkOnSurface,
    surface            = LadonDarkSurface,
    onSurface          = LadonDarkOnSurface,
    surfaceContainer   = LadonDarkSurfaceContainer,
    surfaceContainerHigh = LadonDarkSurfaceContainerHi,
    surfaceVariant     = LadonDarkSurfaceContainer,
    onSurfaceVariant   = LadonDarkOnSurfaceVariant,
    outline            = LadonDarkOutline,
    outlineVariant     = LadonDarkOutlineVariant,
    error              = LadonDarkError,
    onError            = LadonDarkOnPrimary,
    tertiary           = LadonDarkSigValid,
)

private val LadonLight = lightColorScheme(
    primary            = LadonLightPrimary,
    onPrimary          = LadonLightOnPrimary,
    primaryContainer   = LadonLightPrimaryContainer,
    onPrimaryContainer = LadonLightOnSurface,
    background         = LadonLightBackground,
    onBackground       = LadonLightOnSurface,
    surface            = LadonLightSurface,
    onSurface          = LadonLightOnSurface,
    surfaceContainer   = LadonLightSurfaceContainer,
    surfaceContainerHigh = LadonLightSurfaceContainerHi,
    surfaceVariant     = LadonLightSurfaceContainer,
    onSurfaceVariant   = LadonLightOnSurfaceVariant,
    outline            = LadonLightOutline,
    outlineVariant     = LadonLightOutlineVariant,
    error              = LadonLightError,
    onError            = LadonLightOnPrimary,
    tertiary           = LadonLightSigValid,
)

/** El color de "estado desconocido" no tiene rol M3: se expone aparte. */
val MaterialTheme.sigUnknown: Color
    @Composable get() = if (isSystemInDarkTheme()) LadonDarkSigUnknown else LadonLightSigUnknown

/**
 * SIN color dinámico, a propósito: en DracPDF el color es información legal
 * (verde/ámbar/rojo de firma, dos resaltados de búsqueda). Un acento tomado del
 * fondo de pantalla podría acabar a un paso del verde "firma válida".
 */
@Composable
fun DracPdfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) LadonDark else LadonLight
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)   // edge-to-edge siempre
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = LadonTypography, shapes = LadonShapes, content = content)
}
