package com.marcmayol.dracpdf.dominio

import com.marcmayol.dracpdf.dominio.casos.BuscarEnDocumento
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.PuntoPt
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.modelo.SeleccionTexto
import com.marcmayol.dracpdf.dominio.puertos.TextoPdf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El recorrido de la búsqueda: por dónde empieza, por dónde sigue y cuándo para.
 *
 * Se prueba sin motor porque lo que hay que demostrar no es que MuPDF encuentre
 * texto —eso es suyo— sino el orden en que se le pregunta, que es lo que hace que
 * «siguiente» lleve a donde el lector espera.
 */
class BuscarEnDocumentoTest {
    private val id = IdDocumento("prueba")

    @Test
    fun empieza_por_la_pagina_que_se_esta_mirando_y_da_la_vuelta() {
        val motor = TextoFalso(dondeAparece = listOf(1, 3, 8))
        val buscar = BuscarEnDocumento(motor)

        val encontradas = buscar(id, "total", paginas = 10, desde = 5)

        // Desde la 5: primero la 8, y al dar la vuelta, la 1 y la 3.
        assertEquals(listOf(8, 1, 3), encontradas.map { it.pagina })
        assertEquals(listOf(5, 6, 7, 8, 9, 0, 1, 2, 3, 4), motor.preguntadas)
    }

    @Test
    fun parar_en_la_primera_pagina_con_resultados_no_recorre_el_resto() {
        val motor = TextoFalso(dondeAparece = listOf(2, 4, 6))
        val buscar = BuscarEnDocumento(motor)

        val encontradas = buscar(id, "total", paginas = 10, desde = 0, alEncontrar = { false })

        assertEquals(listOf(2), encontradas.map { it.pagina })
        // Se paró en cuanto encontró: no siguió mirando las otras siete.
        assertEquals(listOf(0, 1, 2), motor.preguntadas)
    }

    @Test
    fun buscar_nada_no_pregunta_al_motor() {
        val motor = TextoFalso(dondeAparece = listOf(1))

        assertTrue(BuscarEnDocumento(motor)(id, "   ", paginas = 5).isEmpty())
        assertTrue("Se buscó el vacío en el documento", motor.preguntadas.isEmpty())
    }

    @Test
    fun una_pagina_de_partida_fuera_del_documento_no_rompe_la_busqueda() {
        val motor = TextoFalso(dondeAparece = listOf(0))

        // Puede pasar de verdad: el documento cambia de tamaño al organizarlo mientras
        // la página guardada sigue siendo la vieja.
        val encontradas = BuscarEnDocumento(motor)(id, "total", paginas = 3, desde = 99)

        assertEquals(listOf(0), encontradas.map { it.pagina })
    }

    /** Un motor que dice tener la palabra en las páginas que se le digan. */
    private class TextoFalso(
        private val dondeAparece: List<Int>,
    ) : TextoPdf {
        val preguntadas = mutableListOf<Int>()

        override fun buscar(
            id: IdDocumento,
            pagina: Int,
            texto: String,
        ): List<RectPt> {
            preguntadas += pagina
            return if (pagina in dondeAparece) listOf(RectPt(0f, 0f, 10f, 10f)) else emptyList()
        }

        override fun palabraEn(
            id: IdDocumento,
            pagina: Int,
            punto: PuntoPt,
        ): SeleccionTexto? = null

        override fun seleccionEntre(
            id: IdDocumento,
            pagina: Int,
            desde: PuntoPt,
            hasta: PuntoPt,
        ): SeleccionTexto = SeleccionTexto.NINGUNA

        override fun textoDe(
            id: IdDocumento,
            pagina: Int,
        ): String = ""
    }
}
