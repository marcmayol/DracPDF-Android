package com.marcmayol.dracpdf.ui.tema

/**
 * Preferencia de tema del usuario. Se obedece al sistema en lo que no es identidad
 * —claro/oscuro, escala de fuente, alto contraste, reducción de movimiento— pero
 * nunca en el color: el acento de Ladón no se negocia con el fondo de pantalla.
 */
enum class PreferenciaTema {
    CLARO,
    OSCURO,
    SISTEMA,
}
