package com.dracpdf.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Identidad Ladón · esquema oscuro ───────────────────────────────
val LadonDarkPrimary            = Color(0xFFE0534A)
val LadonDarkOnPrimary          = Color(0xFF1A1D23)
val LadonDarkPrimaryContainer   = Color(0xFF3A2523)
val LadonDarkBackground         = Color(0xFF14161A)
val LadonDarkSurface            = Color(0xFF1A1D23)
val LadonDarkSurfaceContainer   = Color(0xFF22262E)
val LadonDarkSurfaceContainerHi = Color(0xFF2A2F39)
val LadonDarkOnSurface          = Color(0xFFE9EBF0)
val LadonDarkOnSurfaceVariant   = Color(0xFF98A0B0)
val LadonDarkOutline            = Color(0xFF343A46)
val LadonDarkOutlineVariant     = Color(0xFF2A2F39)
val LadonDarkError              = Color(0xFFE07B6E)
val LadonDarkSigValid           = Color(0xFF6FBF87)
val LadonDarkSigUnknown         = Color(0xFFD9B45C)

// ─── Identidad Ladón · esquema claro ────────────────────────────────
val LadonLightPrimary            = Color(0xFFA83228)
val LadonLightOnPrimary          = Color(0xFFFFFFFF)
val LadonLightPrimaryContainer   = Color(0xFFF3D9D5)
val LadonLightBackground         = Color(0xFFD8DAE0)
val LadonLightSurface            = Color(0xFFF2F2F5)
val LadonLightSurfaceContainer   = Color(0xFFFFFFFF)
val LadonLightSurfaceContainerHi = Color(0xFFE9EAEF)
val LadonLightOnSurface          = Color(0xFF22252C)
val LadonLightOnSurfaceVariant   = Color(0xFF6A7080)
val LadonLightOutline            = Color(0xFFC9CCD6)
val LadonLightOutlineVariant     = Color(0xFFE0E2E9)
val LadonLightError              = Color(0xFFB23B2E)
val LadonLightSigValid           = Color(0xFF2E7D4F)
val LadonLightSigUnknown         = Color(0xFF96741F)

/**
 * Colores anclados al PAPEL, no al tema: el documento siempre se pinta claro,
 * así que estos valores son idénticos en claro y oscuro. No van en el ColorScheme.
 */
object PaperColors {
    val SearchMatch   = Color(0x66D9B45C)   // resto de coincidencias
    val SearchActive  = Color(0x80E0534A)   // coincidencia activa (+ borde 1.5dp SearchActiveBorder)
    val SearchActiveBorder = Color(0xFFA83228)
    val Selection     = Color(0x665B86BD)   // selección de texto
    val SelectionHandle = Color(0xFF5B86BD)
    val FieldPending  = Color(0x1FD9B45C)   // campo de formulario pendiente
    val FieldPendingBorder = Color(0xFFC9A24A)
    val FieldActive   = Color(0x0FE0534A)   // campo activo / marco de colocación
    val FieldActiveBorder  = Color(0xFFA83228)
    val Paper         = Color(0xFFFDFDFC)
    val Ink           = Color(0xFF111318)   // trazo de firma dibujada
}

/** Marcado de anotaciones — los cinco colores de la fila de marcado (§22). */
object MarkupColors {
    val Amber  = Color(0xFFD9B45C)
    val Green  = Color(0xFF6FBF87)
    val Blue   = Color(0xFF5B86BD)
    val Red    = Color(0xFFE07B6E)
    val Purple = Color(0xFFB48AD9)
}
