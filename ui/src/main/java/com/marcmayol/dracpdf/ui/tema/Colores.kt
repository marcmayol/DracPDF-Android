package com.marcmayol.dracpdf.ui.tema

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Tokens de la identidad Ladón, tal como los fija la sección 16 del diseño. Son la
// única fuente de color de la aplicación: ningún Color(0xFF...) suelto por las
// pantallas. Los nombres son los del diseño, no los del rol de Material.

private val Accent = Color(0xFFE0534A)
private val AccentClaro = Color(0xFFA83228)
private val OnAccentOscuro = Color(0xFF1A1D23)
private val OnAccentClaro = Color(0xFFFFFFFF)
private val AccentContenedorOscuro = Color(0xFF3A2523)
private val AccentContenedorClaro = Color(0xFFF3D9D5)

private val CanvasOscuro = Color(0xFF14161A)
private val CanvasClaro = Color(0xFFD8DAE0)
private val BgOscuro = Color(0xFF1A1D23)
private val BgClaro = Color(0xFFF2F2F5)
private val SurfaceOscuro = Color(0xFF22262E)
private val SurfaceClaro = Color(0xFFFFFFFF)
private val Surface2Oscuro = Color(0xFF2A2F39)
private val Surface2Claro = Color(0xFFE9EAEF)

private val TextoOscuro = Color(0xFFE9EBF0)
private val TextoClaro = Color(0xFF22252C)
private val TextoApagadoOscuro = Color(0xFF98A0B0)
private val TextoApagadoClaro = Color(0xFF6A7080)

private val BordeOscuro = Color(0xFF343A46)
private val BordeClaro = Color(0xFFC9CCD6)
private val BordeSuaveOscuro = Color(0xFF2A2F39)
private val BordeSuaveClaro = Color(0xFFE0E2E9)

private val FirmaInvalidaOscuro = Color(0xFFE07B6E)
private val FirmaInvalidaClaro = Color(0xFFB23B2E)
private val FirmaValidaOscuro = Color(0xFF6FBF87)
private val FirmaValidaClaro = Color(0xFF2E7D4F)
private val FirmaDesconocidaOscuro = Color(0xFFD9B45C)
private val FirmaDesconocidaClaro = Color(0xFF96741F)

/**
 * Esquema oscuro. Nunca se usa color dinámico: en esta aplicación el color es
 * información legal —el verde, el ámbar y el rojo de las firmas, y los dos
 * resaltados de la búsqueda— y tiene que sobrevivir a cualquier fondo de pantalla.
 * La confianza no se tematiza.
 */
val EsquemaOscuroLadon: ColorScheme =
    darkColorScheme(
        primary = Accent,
        onPrimary = OnAccentOscuro,
        primaryContainer = AccentContenedorOscuro,
        onPrimaryContainer = TextoOscuro,
        secondary = TextoApagadoOscuro,
        onSecondary = OnAccentOscuro,
        secondaryContainer = Surface2Oscuro,
        onSecondaryContainer = TextoOscuro,
        tertiary = FirmaValidaOscuro,
        onTertiary = OnAccentOscuro,
        background = CanvasOscuro,
        onBackground = TextoOscuro,
        surface = BgOscuro,
        onSurface = TextoOscuro,
        surfaceVariant = SurfaceOscuro,
        onSurfaceVariant = TextoApagadoOscuro,
        surfaceContainerLowest = CanvasOscuro,
        surfaceContainerLow = BgOscuro,
        surfaceContainer = SurfaceOscuro,
        surfaceContainerHigh = Surface2Oscuro,
        surfaceContainerHighest = Surface2Oscuro,
        error = FirmaInvalidaOscuro,
        onError = OnAccentOscuro,
        outline = BordeOscuro,
        outlineVariant = BordeSuaveOscuro,
        scrim = Color(0xFF0A0B0E),
    )

/** Esquema claro, con el acento en brasa oscura para que contraste sobre blanco. */
val EsquemaClaroLadon: ColorScheme =
    lightColorScheme(
        primary = AccentClaro,
        onPrimary = OnAccentClaro,
        primaryContainer = AccentContenedorClaro,
        onPrimaryContainer = TextoClaro,
        secondary = TextoApagadoClaro,
        onSecondary = OnAccentClaro,
        secondaryContainer = Surface2Claro,
        onSecondaryContainer = TextoClaro,
        tertiary = FirmaValidaClaro,
        onTertiary = OnAccentClaro,
        background = CanvasClaro,
        onBackground = TextoClaro,
        surface = BgClaro,
        onSurface = TextoClaro,
        surfaceVariant = SurfaceClaro,
        onSurfaceVariant = TextoApagadoClaro,
        surfaceContainerLowest = CanvasClaro,
        surfaceContainerLow = BgClaro,
        surfaceContainer = SurfaceClaro,
        surfaceContainerHigh = Surface2Claro,
        surfaceContainerHighest = Surface2Claro,
        error = FirmaInvalidaClaro,
        onError = OnAccentClaro,
        outline = BordeClaro,
        outlineVariant = BordeSuaveClaro,
        scrim = Color(0xFF0A0B0E),
    )

/**
 * Lo que no cabe en un [ColorScheme] de Material. El estado «desconocido» de una
 * firma no es error ni éxito: es una tercera cosa y necesita su propio color.
 */
@Immutable
data class ColoresLadon(
    val firmaDesconocida: Color,
    val handleSeleccion: Color,
)

val ColoresLadonOscuro =
    ColoresLadon(
        firmaDesconocida = FirmaDesconocidaOscuro,
        handleSeleccion = Color(0xFF5B86BD),
    )

val ColoresLadonClaro =
    ColoresLadon(
        firmaDesconocida = FirmaDesconocidaClaro,
        handleSeleccion = Color(0xFF5B86BD),
    )

val LocalColoresLadon = staticCompositionLocalOf { ColoresLadonOscuro }

/**
 * El papel. Estos cinco valores son idénticos en claro y en oscuro a propósito: el
 * documento siempre se pinta claro, y lo que se dibuja encima —resaltados,
 * selección, campos de formulario— pertenece al papel, no a la aplicación. Por eso
 * viven fuera del [ColorScheme]: cambiar de tema no puede moverlos.
 */
object ColoresPapel {
    /** Fondo del propio documento. */
    val papel = Color(0xFFFDFDFC)

    /** Coincidencias de búsqueda. */
    val coincidencia = Color(0x66D9B45C)

    /** Coincidencia activa; lleva además borde de 1,5 dp en [coincidenciaActivaBorde]. */
    val coincidenciaActiva = Color(0x80E0534A)
    val coincidenciaActivaBorde = Color(0xFFA83228)

    /** Selección de texto; los handles van en [ColoresLadon.handleSeleccion]. */
    val seleccion = Color(0x665B86BD)

    /** Campo de formulario aún sin rellenar. */
    val campoPendiente = Color(0x1FD9B45C)
    val campoPendienteBorde = Color(0xFFC9A24A)

    /** Campo activo, y marco del modo de colocación. */
    val campoActivo = Color(0x0FE0534A)
    val campoActivoBorde = Color(0xFFA83228)

    /** Tinta de la firma dibujada a mano. Nunca se recolorea. */
    val tinta = Color(0xFF111318)
}

/**
 * Los cinco colores del marcado de anotaciones (resaltar, subrayar, tachar).
 *
 * También son del papel: una anotación se guarda dentro del PDF y tiene que verse
 * igual en cualquier otro visor, así que el tema de la aplicación no la toca.
 */
object ColoresMarcado {
    val ambar = Color(0xFFD9B45C)
    val verde = Color(0xFF6FBF87)
    val azul = Color(0xFF5B86BD)
    val rojo = Color(0xFFE07B6E)
    val morado = Color(0xFFB48AD9)

    val todos = listOf(ambar, verde, azul, rojo, morado)
}
