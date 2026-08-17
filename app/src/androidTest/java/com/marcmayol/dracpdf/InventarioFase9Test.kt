package com.marcmayol.dracpdf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.marcmayol.dracpdf.ui.herramientas.DestinoDeConversion
import com.marcmayol.dracpdf.ui.herramientas.DestinoDeTabla
import com.marcmayol.dracpdf.ui.herramientas.DialogoConvertir
import com.marcmayol.dracpdf.ui.herramientas.Herramienta
import com.marcmayol.dracpdf.ui.herramientas.TAG_CONVERTIR_ACEPTAR
import com.marcmayol.dracpdf.ui.herramientas.TAG_CONVERTIR_EXPLICACION
import com.marcmayol.dracpdf.ui.herramientas.TAG_DIALOGO_CONVERTIR
import com.marcmayol.dracpdf.ui.inicio.PantallaInicio
import com.marcmayol.dracpdf.ui.inicio.TAG_MENU_CREAR_PDF
import com.marcmayol.dracpdf.ui.inicio.TAG_MENU_ESCANEAR
import com.marcmayol.dracpdf.ui.inicio.TAG_MENU_INICIO
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Inventario de la Fase 9: las conversiones.
 *
 * Lo que vigila este inventario es lo que el plan repite tres veces: **una sola entrada
 * de conversión**. Ocho destinos distintos dentro de un diálogo, y ni una segunda
 * entrada llamada «Exportar» en la caja de herramientas, que es el error que el
 * escritorio tuvo que deshacer en su 0.4.0.
 */
@RunWith(AndroidJUnit4::class)
class InventarioFase9Test {
    @get:Rule
    val composicion = createComposeRule()

    @Test
    fun sigue_habiendo_una_sola_entrada_de_conversion_con_todos_los_destinos_dentro() {
        val deConversion = Herramienta.entries.filter { "onvertir" in it.etiqueta || "xportar" in it.etiqueta }
        assertEquals("Hay más de una entrada de conversión: $deConversion", 1, deConversion.size)

        composicion.setContent {
            TemaDracPDF {
                DialogoConvertir(paginas = 4, alElegirTexto = {}, alElegirImagenes = { _, _ -> }, alCancelar = {})
            }
        }
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_DIALOGO_CONVERTIR).assertIsDisplayed()
        DestinoDeConversion.entries.forEach { destino ->
            composicion.onNodeWithTag(destino.tag).assertIsDisplayed()
        }
    }

    @Test
    fun cada_destino_explica_lo_que_va_a_pasar() {
        var pedido: DestinoDeConversion? = null
        composicion.setContent {
            TemaDracPDF {
                DialogoConvertir(
                    paginas = 4,
                    alElegirTexto = {},
                    alElegirImagenes = { _, _ -> },
                    alElegirDocumento = { pedido = it },
                    alCancelar = {},
                )
            }
        }
        composicion.waitForIdle()

        // Word avisa de que la maquetación no se conserva: prometer el diseño exacto
        // sería mentir, y el escritorio ya aprendió esa lección.
        composicion.onNodeWithTag(DestinoDeConversion.WORD.tag).performClick()
        composicion.onNodeWithTag(TAG_CONVERTIR_EXPLICACION).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_CONVERTIR_ACEPTAR).performClick()

        assertEquals(DestinoDeConversion.WORD, pedido)
    }

    @Test
    fun las_tablas_dejan_elegir_entre_csv_y_xlsx() {
        var formato: DestinoDeTabla? = null
        composicion.setContent {
            TemaDracPDF {
                DialogoConvertir(
                    paginas = 4,
                    alElegirTexto = {},
                    alElegirImagenes = { _, _ -> },
                    alElegirTablas = { formato = it },
                    alCancelar = {},
                )
            }
        }
        composicion.waitForIdle()

        composicion.onNodeWithTag(DestinoDeConversion.TABLAS.tag).performClick()
        composicion.onNodeWithTag(DestinoDeTabla.XLSX.tag).performClick()
        composicion.onNodeWithTag(TAG_CONVERTIR_ACEPTAR).performClick()

        assertEquals(DestinoDeTabla.XLSX, formato)
    }

    @Test
    fun crear_un_pdf_y_escanear_son_dos_entradas_distintas_del_inicio() {
        var creando = false
        var escaneando = false
        composicion.setContent {
            TemaDracPDF {
                PantallaInicio(
                    alAbrirPdf = {},
                    alCrearPdf = { creando = true },
                    alEscanear = { escaneando = true },
                )
            }
        }
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_MENU_INICIO).performClick()
        composicion.waitForIdle()
        composicion.onNodeWithTag(TAG_MENU_CREAR_PDF).performClick()
        composicion.waitForIdle()
        composicion.onNodeWithTag(TAG_MENU_INICIO).performClick()
        composicion.waitForIdle()
        composicion.onNodeWithTag(TAG_MENU_ESCANEAR).performClick()

        // Son dos cosas distintas y por eso son dos entradas: escanear no es convertir
        // algo que ya existe, es fabricarlo con la cámara.
        assertTrue("«Crear un PDF» no lleva a ninguna parte", creando)
        assertTrue("«Escanear» no lleva a ninguna parte", escaneando)
    }

    @Test
    fun los_destinos_que_escriben_varios_ficheros_van_a_una_carpeta() {
        // Un documento de texto es un fichero y se puede pedir su nombre; unas tablas
        // son varios y unas imágenes son una por página. Preguntar «cómo lo llamo» para
        // algo que va a producir seis ficheros sería mentir sobre lo que va a pasar.
        assertTrue(DestinoDeConversion.TABLAS.vaACarpeta)
        assertTrue(DestinoDeConversion.IMAGENES.vaACarpeta)
        assertTrue(DestinoDeConversion.HTML.vaACarpeta)
        assertTrue("Un .txt es un fichero suelto", !DestinoDeConversion.TEXTO.vaACarpeta)
        assertTrue("Un .docx también", !DestinoDeConversion.WORD.vaACarpeta)
    }
}
