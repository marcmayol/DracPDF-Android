package com.marcmayol.dracpdf.ui.tema

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lo que se lee del disco no siempre es lo que se escribió: una versión anterior, un
 * fichero tocado a mano, un valor que se renombró. El arranque no puede caerse por
 * eso.
 */
class PreferenciaTemaTest {
    @Test
    fun cada_preferencia_se_reconoce_por_su_nombre() {
        PreferenciaTema.entries.forEach { preferencia ->
            assertEquals(preferencia, PreferenciaTema.de(preferencia.name))
        }
    }

    @Test
    fun sin_nada_guardado_manda_el_sistema() {
        assertEquals(PreferenciaTema.SISTEMA, PreferenciaTema.de(null))
    }

    @Test
    fun un_valor_desconocido_no_tumba_el_arranque() {
        assertEquals(PreferenciaTema.SISTEMA, PreferenciaTema.de("MEDIA_LUZ"))
        assertEquals(PreferenciaTema.SISTEMA, PreferenciaTema.de(""))
        // Y las mayúsculas cuentan: se guarda el nombre del enumerado, no un texto
        // libre, así que no se adivina.
        assertEquals(PreferenciaTema.SISTEMA, PreferenciaTema.de("claro"))
    }
}
