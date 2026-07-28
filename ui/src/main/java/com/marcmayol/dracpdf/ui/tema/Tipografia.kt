package com.marcmayol.dracpdf.ui.tema

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Roboto y Roboto Mono del sistema: ninguna fuente empaquetada. Roboto hace en el
// móvil el papel de Segoe UI / Noto Sans en el escritorio, y Roboto Mono el de
// JetBrains Mono para los datos técnicos (página, hash, número de serie).
//
// Los tamaños son los de la sección 16 del diseño. Van en sp, no en dp, para que el
// escalado de fuente del sistema los mueva hasta el 200 % como manda el diseño; por
// eso ningún contenedor con texto puede llevar altura fija.

val TipografiaLadon =
    Typography(
        displaySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 36.sp,
                lineHeight = 44.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            ),
    )

/**
 * Estilo de los datos técnicos: número de página, hash, número de serie del
 * certificado. Es `labelSmall`, que ya es monoespaciada, con nombre propio para que
 * en las pantallas se lea la intención y no el rol de Material.
 */
val EstiloMono = TipografiaLadon.labelSmall
