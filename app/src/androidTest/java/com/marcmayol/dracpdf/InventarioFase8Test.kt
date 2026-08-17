package com.marcmayol.dracpdf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.marcmayol.dracpdf.dominio.modelo.Anotacion
import com.marcmayol.dracpdf.dominio.modelo.ColorAnotacion
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.modelo.TipoAnotacion
import com.marcmayol.dracpdf.ui.herramientas.Herramienta
import com.marcmayol.dracpdf.ui.herramientas.HojaHerramientas
import com.marcmayol.dracpdf.ui.herramientas.disponible
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import com.marcmayol.dracpdf.ui.visor.BarraSeleccion
import com.marcmayol.dracpdf.ui.visor.HojaAnotaciones
import com.marcmayol.dracpdf.ui.visor.TAG_HOJA_ANOTACIONES
import com.marcmayol.dracpdf.ui.visor.TAG_SELECCION_COPIAR
import com.marcmayol.dracpdf.ui.visor.TAG_SELECCION_RESALTAR
import com.marcmayol.dracpdf.ui.visor.TAG_SELECCION_SUBRAYAR
import com.marcmayol.dracpdf.ui.visor.TAG_SELECCION_TACHAR
import com.marcmayol.dracpdf.ui.visor.TAG_SIN_ANOTACIONES
import com.marcmayol.dracpdf.ui.visor.tagBorrarAnotacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Inventario de la Fase 8: marcar y editar.
 *
 * Las marcas se piden **sobre el texto seleccionado**, así que su sitio es la barra de
 * selección y no una herramienta aparte: resaltar «una zona» de la página no significa
 * nada. Y tienen que poder quitarse, que es la otra mitad de lo que promete la fase.
 */
@RunWith(AndroidJUnit4::class)
class InventarioFase8Test {
    @get:Rule
    val composicion = createComposeRule()

    @Test
    fun con_texto_seleccionado_se_puede_resaltar_subrayar_y_tachar() {
        val pedidas = mutableListOf<TipoAnotacion>()
        composicion.setContent {
            TemaDracPDF {
                BarraSeleccion(alCopiar = {}, alCompartir = {}, alMarcar = { pedidas += it })
            }
        }
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_SELECCION_RESALTAR).performClick()
        composicion.onNodeWithTag(TAG_SELECCION_SUBRAYAR).performClick()
        composicion.onNodeWithTag(TAG_SELECCION_TACHAR).performClick()

        assertEquals(
            listOf(TipoAnotacion.RESALTADO, TipoAnotacion.SUBRAYADO, TipoAnotacion.TACHADO),
            pedidas,
        )
        // Y siguen estando las dos de siempre: marcar no ha desplazado a copiar.
        composicion.onNodeWithTag(TAG_SELECCION_COPIAR).assertIsDisplayed()
    }

    @Test
    fun las_marcas_de_la_pagina_se_listan_y_se_quitan() {
        var borrada: Anotacion? = null
        val marcas =
            listOf(
                Anotacion(0, 0, TipoAnotacion.RESALTADO, listOf(RectPt(0f, 0f, 10f, 10f))),
                Anotacion(0, 1, TipoAnotacion.NOTA, listOf(RectPt(0f, 0f, 10f, 10f)), "Revisar"),
            )
        composicion.setContent {
            TemaDracPDF {
                HojaAnotaciones(pagina = 0, anotaciones = marcas, alBorrar = { borrada = it }, alCerrar = {})
            }
        }
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_HOJA_ANOTACIONES).assertIsDisplayed()
        composicion.onNodeWithTag(tagBorrarAnotacion(1)).performClick()

        assertEquals(TipoAnotacion.NOTA, borrada?.tipo)
    }

    @Test
    fun una_pagina_sin_marcas_lo_dice_y_explica_como_ponerlas() {
        composicion.setContent {
            TemaDracPDF { HojaAnotaciones(pagina = 3, anotaciones = emptyList(), alBorrar = {}, alCerrar = {}) }
        }
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_SIN_ANOTACIONES).assertIsDisplayed()
    }

    @Test
    fun anotaciones_ya_no_esta_apagada_en_la_caja_de_herramientas() {
        composicion.setContent { TemaDracPDF { HojaHerramientas(alElegir = {}, alCerrar = {}) } }
        composicion.waitForIdle()

        composicion.onNodeWithTag(Herramienta.ANOTACIONES.tag).assertIsEnabled()
        assertTrue(Herramienta.ANOTACIONES.disponible(documentoFirmado = false))
        // Pero un documento firmado sigue sin dejarse marcar: una anotación nueva le
        // rompería la firma igual que cualquier otro cambio.
        assertFalse(Herramienta.ANOTACIONES.disponible(documentoFirmado = true))
    }

    @Test
    fun los_colores_de_marca_son_pocos_y_con_nombre() {
        // Cuatro colores con nombre y no una rueda: en un móvil, un resaltado sirve para
        // distinguir tres o cuatro cosas, y elegir entre millones es una decisión que no
        // aporta nada al documento.
        assertEquals(4, ColorAnotacion.entries.size)
    }
}
