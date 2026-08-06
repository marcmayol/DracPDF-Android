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
    ;

    companion object {
        /**
         * La preferencia que hay guardada bajo ese nombre.
         *
         * Lo que no se reconoce —no hay nada guardado todavía, o el valor viene de una
         * versión que ya no existe— se lee como [SISTEMA]: obedecer al teléfono es el
         * único comportamiento que nunca sorprende a quien abre la aplicación.
         */
        fun de(guardado: String?): PreferenciaTema = entries.firstOrNull { it.name == guardado } ?: SISTEMA
    }
}
