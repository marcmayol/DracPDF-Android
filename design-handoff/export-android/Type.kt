package com.dracpdf.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Roboto del sistema: ninguna fuente empaquetada (equivale a Segoe UI / Noto Sans en escritorio).
val LadonTypography = Typography(
    displaySmall  = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge    = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium   = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium    = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall     = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge    = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelSmall    = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
)

/** Datos técnicos: hash, nº de serie, nº de página. Papel de JetBrains Mono en escritorio. */
val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp)
