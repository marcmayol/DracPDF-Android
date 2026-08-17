package com.marcmayol.dracpdf.ui.herramientas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.marcmayol.dracpdf.dominio.puertos.PaginaOrdenada
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Organizar páginas, desde la interfaz real.
 *
 * Lo que se comprueba no es que los botones existan sino **lo que se va a guardar**:
 * la lista de páginas que sale por «Guardar» es el documento nuevo, y es la única
 * manera honesta de verificar que girar, eliminar, extraer y reordenar hacen lo que
 * dicen.
 */
@RunWith(AndroidJUnit4::class)
class OrganizarPaginasTest {
    @get:Rule
    val composicion = createComposeRule()

    private var guardado: List<PaginaOrdenada>? = null

    @Test
    fun sin_tocar_nada_se_guarda_el_documento_tal_cual() {
        montarCon(paginas = 4)

        composicion.onNodeWithTag(TAG_ORGANIZAR_GUARDAR).performClick()

        assertEquals((0..3).map { PaginaOrdenada(original = it) }, guardado)
    }

    @Test
    fun girar_la_seleccionada_la_deja_a_un_cuarto_de_vuelta() {
        montarCon(paginas = 3)

        composicion.onNodeWithTag(tagPaginaOrganizar(1)).performClick()
        composicion.onNodeWithTag(TAG_ORGANIZAR_ROTAR).performClick()
        composicion.onNodeWithTag(TAG_ORGANIZAR_ROTAR).performClick()
        composicion.onNodeWithTag(TAG_ORGANIZAR_GUARDAR).performClick()

        assertEquals(
            listOf(
                PaginaOrdenada(original = 0),
                PaginaOrdenada(original = 1, giro = 180),
                PaginaOrdenada(original = 2),
            ),
            guardado,
        )
    }

    @Test
    fun eliminar_deja_fuera_lo_seleccionado() {
        montarCon(paginas = 4)

        composicion.onNodeWithTag(tagPaginaOrganizar(0)).performClick()
        composicion.onNodeWithTag(tagPaginaOrganizar(2)).performClick()
        composicion.onNodeWithTag(TAG_ORGANIZAR_ELIMINAR).performClick()
        composicion.onNodeWithTag(TAG_ORGANIZAR_GUARDAR).performClick()

        assertEquals(listOf(PaginaOrdenada(original = 1), PaginaOrdenada(original = 3)), guardado)
    }

    @Test
    fun extraer_se_queda_solo_con_lo_seleccionado() {
        montarCon(paginas = 5)

        composicion.onNodeWithTag(tagPaginaOrganizar(3)).performClick()
        composicion.onNodeWithTag(TAG_ORGANIZAR_EXTRAER).performClick()
        composicion.onNodeWithTag(TAG_ORGANIZAR_GUARDAR).performClick()

        assertEquals(listOf(PaginaOrdenada(original = 3)), guardado)
    }

    @Test
    fun sin_seleccion_no_hay_nada_que_hacerle_a_las_paginas() {
        montarCon(paginas = 3)

        // Un botón encendido que no hace nada es peor que uno apagado: el usuario
        // pulsa y cree que la aplicación se ha colgado.
        composicion.onNodeWithTag(TAG_ORGANIZAR_ROTAR).assertIsNotEnabled()
        composicion.onNodeWithTag(TAG_ORGANIZAR_ELIMINAR).assertIsNotEnabled()
        composicion.onNodeWithTag(TAG_ORGANIZAR_EXTRAER).assertIsNotEnabled()

        composicion.onNodeWithTag(tagPaginaOrganizar(0)).performClick()
        composicion.onNodeWithTag(TAG_ORGANIZAR_ROTAR).assertIsEnabled()
    }

    @Test
    fun eliminarlo_todo_no_es_organizar_y_no_se_deja() {
        montarCon(paginas = 2)

        composicion.onNodeWithTag(tagPaginaOrganizar(0)).performClick()
        composicion.onNodeWithTag(tagPaginaOrganizar(1)).performClick()

        // Un PDF sin páginas no es un PDF, y extraerlas todas no extrae nada.
        composicion.onNodeWithTag(TAG_ORGANIZAR_ELIMINAR).assertIsNotEnabled()
        composicion.onNodeWithTag(TAG_ORGANIZAR_EXTRAER).assertIsNotEnabled()
    }

    @Test
    fun arrastrar_una_pagina_sobre_otra_las_intercambia_de_sitio() {
        montarCon(paginas = 6)

        arrastrar(desde = 0, hasta = 2)
        composicion.onNodeWithTag(TAG_ORGANIZAR_GUARDAR).performClick()

        // La primera se ha ido al tercer hueco y las otras dos han corrido una a la
        // izquierda: es mover, no intercambiar.
        assertEquals(listOf(1, 2, 0, 3, 4, 5), guardado?.map { it.original })
    }

    @Test
    fun mantener_pulsada_una_pagina_la_coge_para_moverla() {
        montarCon(paginas = 6)

        val rejilla = composicion.onNodeWithTag(TAG_REJILLA_ORGANIZAR).fetchSemanticsNode().boundsInRoot
        val origen = centroDelHueco(0) - rejilla.topLeft
        composicion.onNodeWithTag(TAG_REJILLA_ORGANIZAR).performTouchInput {
            down(origen)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + MARGEN_PULSACION_LARGA)
            moveTo(origen + Offset(1f, 1f))
        }
        composicion.waitForIdle()

        // La cabecera lo dice mientras dura: es la señal de que el gesto es un
        // movimiento de página y no un desplazamiento de la lista.
        composicion.onNodeWithText("Moviendo la p. 1").assertIsDisplayed()
    }

    @Test
    fun la_hoja_dice_cuantas_paginas_quedan() {
        montarCon(paginas = 4)

        composicion.onNodeWithTag(TAG_HOJA_ORGANIZAR).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_ORGANIZAR_RESUMEN).assertIsDisplayed()
    }

    /**
     * Mantiene pulsada la página del hueco [desde] y la lleva hasta el hueco [hasta].
     *
     * El gesto va sobre la rejilla y no sobre la celda porque es la rejilla la que lo
     * escucha: la celda no sabe dónde están las demás. La espera entre el toque y el
     * primer movimiento es la pulsación larga de verdad, la del sistema, y sin ella el
     * gesto sería un desplazamiento de la lista.
     */
    private fun arrastrar(
        desde: Int,
        hasta: Int,
    ) {
        val rejilla = composicion.onNodeWithTag(TAG_REJILLA_ORGANIZAR).fetchSemanticsNode().boundsInRoot
        val origen = centroDelHueco(desde) - rejilla.topLeft
        val destino = centroDelHueco(hasta) - rejilla.topLeft

        composicion.onNodeWithTag(TAG_REJILLA_ORGANIZAR).performTouchInput {
            down(origen)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + MARGEN_PULSACION_LARGA)
            // El primer movimiento arranca el arrastre; el segundo lo lleva al destino.
            moveTo(origen + Offset(1f, 1f))
            moveTo(destino)
            up()
        }
        composicion.waitForIdle()
    }

    /**
     * El centro de la miniatura que ocupa un hueco, que es donde el dedo se apoya de
     * verdad. Al empezar, la página N está en el hueco N.
     */
    private fun centroDelHueco(posicion: Int) =
        composicion
            .onNodeWithTag(tagPaginaOrganizar(posicion))
            .fetchSemanticsNode()
            .boundsInRoot.center

    private fun montarCon(paginas: Int) {
        composicion.setContent {
            TemaDracPDF {
                HojaOrganizar(
                    paginas = paginas,
                    // Sin bitmaps: aquí se mide el orden que sale, no lo que se dibuja.
                    miniaturas = emptyMap(),
                    alPedirMiniatura = {},
                    alGuardar = { guardado = it },
                    alCerrar = {},
                )
            }
        }
        composicion.waitForIdle()
    }

    private companion object {
        const val MARGEN_PULSACION_LARGA = 200L
    }
}
