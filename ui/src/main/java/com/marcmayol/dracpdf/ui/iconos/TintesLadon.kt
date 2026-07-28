package com.marcmayol.dracpdf.ui.iconos

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.marcmayol.dracpdf.ui.tema.EsquemaClaroLadon

/**
 * Estado con el que se dibuja un icono. El paquete de identidad entrega seis
 * variantes pre-tintadas por icono (`qt-tinted/{light,dark,accent,ember,muted,
 * disabled}`) porque Qt no sabe recolorear un SVG en caliente; Compose sí, así que
 * en Android el sistema es el mismo pero se expresa con un asset y seis tintes.
 *
 * Esto no es una simplificación del diseño: es la forma que el propio paquete manda
 * usar en Android («los XML llevan color negro literal; el tinte lo pone `Icon`, no
 * el asset»). Lo que se conserva es lo que importa —qué color le toca a cada icono
 * según su estado— en un único sitio.
 */
enum class EstadoIcono {
    /** En reposo. El tema decide si sale claro u oscuro. */
    NORMAL,

    /** Secundario: etiquetas inactivas, metadatos, iconos de apoyo. */
    APAGADO,

    /** Activo, seleccionado, o el destino en curso. */
    ACENTO,

    /** Acento sobre superficie muy clara, donde el rojo brasa vivo se lava. */
    BRASA,

    /** Deshabilitado. */
    DESHABILITADO,

    /** Sobre relleno de acento: el ✓ del FAB, el chip de página seleccionada. */
    SOBRE_ACENTO,
}

private const val ALFA_DESHABILITADO = 0.38f

object TintesLadon {
    @Composable
    @ReadOnlyComposable
    fun de(estado: EstadoIcono): Color =
        when (estado) {
            EstadoIcono.NORMAL -> MaterialTheme.colorScheme.onSurface
            EstadoIcono.APAGADO -> MaterialTheme.colorScheme.onSurfaceVariant
            EstadoIcono.ACENTO -> MaterialTheme.colorScheme.primary
            EstadoIcono.BRASA -> EsquemaClaroLadon.primary
            EstadoIcono.DESHABILITADO ->
                MaterialTheme.colorScheme.onSurface.copy(alpha = ALFA_DESHABILITADO)
            EstadoIcono.SOBRE_ACENTO -> MaterialTheme.colorScheme.onPrimary
        }
}
