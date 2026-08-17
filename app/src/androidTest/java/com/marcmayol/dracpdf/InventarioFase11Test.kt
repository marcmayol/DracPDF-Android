package com.marcmayol.dracpdf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.marcmayol.dracpdf.ui.ajustes.HojaAjustes
import com.marcmayol.dracpdf.ui.ajustes.TAG_AJUSTES_SISTEMA
import com.marcmayol.dracpdf.ui.ajustes.TAG_HOJA_AJUSTES
import com.marcmayol.dracpdf.ui.inicio.PantallaInicio
import com.marcmayol.dracpdf.ui.inicio.TAG_MENU_AJUSTES
import com.marcmayol.dracpdf.ui.inicio.TAG_MENU_INICIO
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Inventario de la Fase 11: la ayuda para ser el lector por defecto.
 *
 * Los filtros del manifest los comprueba `LectorPorDefectoTest` preguntándole al
 * sistema; aquí se mira la otra mitad, la que ve el usuario: que «Ajustes» ya no esté
 * apagado y que la explicación esté escrita para quien no sabe qué es un intent.
 */
@RunWith(AndroidJUnit4::class)
class InventarioFase11Test {
    @get:Rule
    val composicion = createComposeRule()

    @Test
    fun ajustes_ya_se_puede_abrir_desde_el_inicio() {
        var abiertos = false
        composicion.setContent {
            TemaDracPDF { PantallaInicio(alAbrirPdf = {}, alAbrirAjustes = { abiertos = true }) }
        }
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_MENU_INICIO).performClick()
        composicion.waitForIdle()
        composicion.onNodeWithTag(TAG_MENU_AJUSTES).assertIsEnabled()
        composicion.onNodeWithTag(TAG_MENU_AJUSTES).performClick()

        assertTrue("La entrada de Ajustes del menú no lleva a ninguna parte", abiertos)
    }

    @Test
    fun la_hoja_explica_como_hacerlo_predeterminado_y_lleva_a_los_ajustes_del_sistema() {
        var alSistema = false
        composicion.setContent {
            TemaDracPDF {
                HojaAjustes(alAbrirAjustesDelSistema = { alSistema = true }, alCerrar = {})
            }
        }
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_HOJA_AJUSTES).assertIsDisplayed()
        // La explicación nombra las dos palabras que el usuario tiene que buscar en el
        // selector del sistema; sin ellas, la ayuda no ayuda.
        composicion.onNodeWithText("Siempre", substring = true).assertIsDisplayed()

        composicion.onNodeWithTag(TAG_AJUSTES_SISTEMA).performClick()
        assertTrue("El botón no abre los ajustes del sistema", alSistema)
    }

    @Test
    fun si_ya_somos_el_predeterminado_no_se_explica_como_serlo() {
        composicion.setContent {
            TemaDracPDF {
                HojaAjustes(alAbrirAjustesDelSistema = {}, alCerrar = {}, esElPredeterminado = true)
            }
        }
        composicion.waitForIdle()

        // Repetir la receta a quien ya la siguió es ruido: se le dice que está hecho.
        composicion.onNodeWithText("ya se abren aquí", substring = true).assertIsDisplayed()
    }
}
