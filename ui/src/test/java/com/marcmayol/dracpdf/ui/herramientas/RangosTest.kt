package com.marcmayol.dracpdf.ui.herramientas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Los rangos que el usuario escribe al dividir.
 *
 * Es lógica pura y por eso se prueba sin dispositivo: lo que se verifica es la lectura
 * del texto, no cómo se pinta el campo.
 */
class RangosTest {
    @Test
    fun un_rango_incluye_los_dos_extremos() {
        // «1-3» son tres páginas, que es lo que entiende quien lo escribe.
        assertEquals(listOf(1..3), rangosDe("1-3", paginas = 10))
    }

    @Test
    fun una_pagina_suelta_es_su_propio_rango() {
        assertEquals(listOf(7..7), rangosDe("7", paginas = 10))
    }

    @Test
    fun varios_trozos_separados_por_comas() {
        assertEquals(listOf(1..3, 7..7, 9..10), rangosDe("1-3, 7, 9-10", paginas = 10))
    }

    @Test
    fun los_espacios_sobran_y_no_estorban() {
        assertEquals(listOf(1..2, 5..6), rangosDe("  1 - 2 ,   5-6  ", paginas = 6))
    }

    @Test
    fun lo_que_no_cabe_en_el_documento_no_vale() {
        assertNull("La página 30 no existe en un documento de 6", rangosDe("1-30", paginas = 6))
        assertNull(rangosDe("0-2", paginas = 6))
        assertNull(rangosDe("9", paginas = 6))
    }

    @Test
    fun un_rango_del_reves_no_vale() {
        // «6-2» no es un rango vacío que se pueda ignorar: es que quien lo escribió se
        // equivocó, y decirlo es mejor que dividir por donde no quería.
        assertNull(rangosDe("6-2", paginas = 10))
    }

    @Test
    fun lo_que_no_son_numeros_no_vale() {
        assertNull(rangosDe("primera", paginas = 10))
        assertNull(rangosDe("1-", paginas = 10))
        assertNull(rangosDe("", paginas = 10))
        assertNull(rangosDe("   ", paginas = 10))
    }

    @Test
    fun las_comas_de_sobra_se_perdonan() {
        // Escribir «1-2,» al ir a seguir es lo más normal del mundo, y no tiene por qué
        // apagar el botón mientras se piensa el resto.
        assertEquals(listOf(1..2), rangosDe("1-2,", paginas = 10))
    }
}
