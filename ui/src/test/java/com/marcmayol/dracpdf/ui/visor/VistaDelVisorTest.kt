package com.marcmayol.dracpdf.ui.visor

import com.marcmayol.dracpdf.adaptadores.ajustes.VistaGuardada
import com.marcmayol.dracpdf.dominio.modelo.TamanoPt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La vista guardada y lo que se hace con ella.
 *
 * Dos cosas se comprueban aquí y las dos han costado disgustos en otros visores: que un
 * fichero de ajustes de otra versión no impida abrir un documento, y que el punto en el
 * que se toca la pantalla siga siendo el punto que se toca del papel cuando la vista
 * está girada. Lo segundo es un fallo que no se ve venir, porque la página se ve bien y
 * es el dedo el que se equivoca.
 */
class VistaDelVisorTest {
    private val a4 = TamanoPt(ANCHO_PT, ALTO_PT)

    @Test
    fun cada_ajuste_y_cada_giro_se_reconocen_por_su_nombre() {
        AjusteDeVista.entries.forEach { assertEquals(it, AjusteDeVista.de(it.name)) }
        GiroDeVista.entries.forEach { assertEquals(it, GiroDeVista.de(it.name)) }
    }

    @Test
    fun sin_nada_guardado_se_lee_a_ancho_y_sin_girar() {
        val recien = VistaDelVisor.de(VistaGuardada())

        assertEquals(AjusteDeVista.ANCHO, recien.ajuste)
        assertEquals(GiroDeVista.NINGUNO, recien.giro)
        assertFalse(recien.doblePagina)
    }

    @Test
    fun un_valor_desconocido_no_impide_abrir_el_documento() {
        val deOtraVersion = VistaDelVisor.de(VistaGuardada(ajuste = "CONTINUO", giro = "45"))

        assertEquals(AjusteDeVista.ANCHO, deOtraVersion.ajuste)
        assertEquals(GiroDeVista.NINGUNO, deOtraVersion.giro)
    }

    @Test
    fun lo_guardado_vuelve_entero() {
        val guardada = VistaGuardada(ajuste = "PAGINA", doblePagina = true, giro = "TRES_CUARTOS")
        val vuelta = VistaDelVisor.de(guardada)

        assertEquals(AjusteDeVista.PAGINA, vuelta.ajuste)
        assertEquals(GiroDeVista.TRES_CUARTOS, vuelta.giro)
        assertTrue(vuelta.doblePagina)
    }

    @Test
    fun la_doble_pagina_es_un_deseo_que_la_pantalla_puede_no_conceder() {
        val pedida = VistaDelVisor(doblePagina = true)

        // Estrecha: se guarda que la quiere, pero se sigue viendo una sola. Lo pedido no
        // se borra, porque al girar el teléfono tiene que aparecer sin volver a pedirlo.
        assertEquals(1, pedida.paginasPorFila(cabenDos = false))
        assertEquals(2, pedida.paginasPorFila(cabenDos = true))
        assertTrue(pedida.doblePagina)

        // Y quien no la ha pedido no la recibe por tener una tablet.
        assertEquals(1, VistaDelVisor().paginasPorFila(cabenDos = true))
    }

    @Test
    fun el_giro_da_la_vuelta_entera_en_cuatro_toques() {
        var giro = GiroDeVista.NINGUNO
        repeat(VUELTA_ENTERA) { giro = giro.siguiente }

        assertEquals(GiroDeVista.NINGUNO, giro)
        assertEquals(GiroDeVista.UN_CUARTO, GiroDeVista.NINGUNO.siguiente)
    }

    @Test
    fun solo_los_cuartos_impares_ponen_la_pagina_de_lado() {
        assertFalse(GiroDeVista.NINGUNO.intercambiaLados)
        assertTrue(GiroDeVista.UN_CUARTO.intercambiaLados)
        assertFalse(GiroDeVista.MEDIA.intercambiaLados)
        assertTrue(GiroDeVista.TRES_CUARTOS.intercambiaLados)
    }

    @Test
    fun sin_girar_el_dedo_cae_donde_lo_ponen() {
        val punto = GiroDeVista.NINGUNO.puntoEnLaPagina(fraccionX = 0.25f, fraccionY = 0.75f, tamano = a4)

        assertEquals(ANCHO_PT * 0.25f, punto.x, TOLERANCIA)
        assertEquals(ALTO_PT * 0.75f, punto.y, TOLERANCIA)
    }

    /**
     * Un cuarto de vuelta a la derecha lleva la esquina de arriba a la izquierda del
     * papel al extremo de arriba a la derecha de la pantalla. El dedo tiene que hacer
     * el camino contrario, y este es el caso que lo demuestra sin ambigüedad: las
     * cuatro esquinas, que con un signo cambiado saldrían cruzadas.
     */
    @Test
    fun girada_un_cuarto_las_esquinas_se_deshacen_bien() {
        val arribaDerecha = GiroDeVista.UN_CUARTO.puntoEnLaPagina(1f, 0f, a4)
        assertEquals(0f, arribaDerecha.x, TOLERANCIA)
        assertEquals(0f, arribaDerecha.y, TOLERANCIA)

        val arribaIzquierda = GiroDeVista.UN_CUARTO.puntoEnLaPagina(0f, 0f, a4)
        assertEquals(0f, arribaIzquierda.x, TOLERANCIA)
        assertEquals(ALTO_PT, arribaIzquierda.y, TOLERANCIA)

        val abajoDerecha = GiroDeVista.UN_CUARTO.puntoEnLaPagina(1f, 1f, a4)
        assertEquals(ANCHO_PT, abajoDerecha.x, TOLERANCIA)
        assertEquals(0f, abajoDerecha.y, TOLERANCIA)
    }

    @Test
    fun media_vuelta_manda_el_dedo_a_la_esquina_de_enfrente() {
        val punto = GiroDeVista.MEDIA.puntoEnLaPagina(0f, 0f, a4)

        assertEquals(ANCHO_PT, punto.x, TOLERANCIA)
        assertEquals(ALTO_PT, punto.y, TOLERANCIA)
    }

    @Test
    fun tres_cuartos_es_el_camino_de_vuelta_de_un_cuarto() {
        // Girar tres cuartos y luego uno más es no haber girado: aplicando las dos
        // traducciones seguidas, el punto tiene que volver a donde estaba.
        val ida = GiroDeVista.TRES_CUARTOS.puntoEnLaPagina(0.3f, 0.8f, TamanoPt(1f, 1f))
        val vuelta = GiroDeVista.UN_CUARTO.puntoEnLaPagina(ida.x, ida.y, TamanoPt(1f, 1f))

        assertEquals(0.3f, vuelta.x, TOLERANCIA_FRACCION)
        assertEquals(0.8f, vuelta.y, TOLERANCIA_FRACCION)
    }

    private companion object {
        const val ANCHO_PT = 595f
        const val ALTO_PT = 842f
        const val VUELTA_ENTERA = 4
        const val TOLERANCIA = 0.01f
        const val TOLERANCIA_FRACCION = 0.0001f
    }
}
