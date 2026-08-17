package com.marcmayol.dracpdf.ui.visor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.ajustes.AjustesDeInterfaz
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La vista elegida sigue elegida mañana.
 *
 * Se comprueba donde de verdad duele —**releyendo el disco desde un objeto nuevo**, que
 * es lo que ocurre al matar la aplicación y volver a abrirla—, igual que se hizo con el
 * tema. Un test que se fiara del objeto que acaba de escribir pasaría siempre, aunque
 * no se guardase nada.
 */
@RunWith(AndroidJUnit4::class)
class VistaPersistenteTest {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun dejarloComoEstaba() {
        guardar(VistaDelVisor())
    }

    private fun guardar(vista: VistaDelVisor) {
        runBlocking {
            AjustesDeInterfaz(contexto).elegirVista(vista.ajuste.name, vista.doblePagina, vista.giro.name)
        }
    }

    /** Lo que leería el siguiente arranque: otro objeto, el mismo disco. */
    private fun delSiguienteArranque(): VistaDelVisor =
        runBlocking { VistaDelVisor.de(AjustesDeInterfaz(contexto).vista.first()) }

    @Test
    fun los_tres_ajustes_sobreviven_a_un_arranque_nuevo() {
        guardar(VistaDelVisor(ajuste = AjusteDeVista.PAGINA, doblePagina = true, giro = GiroDeVista.UN_CUARTO))

        val recuperada = delSiguienteArranque()

        assertEquals(AjusteDeVista.PAGINA, recuperada.ajuste)
        assertEquals(GiroDeVista.UN_CUARTO, recuperada.giro)
        assertTrue(recuperada.doblePagina)
    }

    @Test
    fun volver_al_ajuste_de_ancho_tambien_se_guarda() {
        guardar(VistaDelVisor(ajuste = AjusteDeVista.PAGINA, doblePagina = true, giro = GiroDeVista.MEDIA))
        guardar(VistaDelVisor())

        // Deshacer una elección es otra elección: quien vuelve al ajuste de ancho tiene
        // que encontrarlo puesto, y no la página completa de antes.
        val recuperada = delSiguienteArranque()

        assertEquals(AjusteDeVista.ANCHO, recuperada.ajuste)
        assertEquals(GiroDeVista.NINGUNO, recuperada.giro)
        assertFalse(recuperada.doblePagina)
    }

    @Test
    fun la_doble_pagina_pedida_se_guarda_aunque_hoy_no_quepa() {
        // Lo que se guarda es el deseo, no el resultado: se pide en la tablet y tiene
        // que seguir pedida al abrir el mismo documento en el móvil, esperando a que la
        // pantalla dé para ello.
        guardar(VistaDelVisor(doblePagina = true))

        val recuperada = delSiguienteArranque()

        assertTrue(recuperada.doblePagina)
        assertEquals(1, recuperada.paginasPorFila(cabenDos = false))
        assertEquals(2, recuperada.paginasPorFila(cabenDos = true))
    }
}
