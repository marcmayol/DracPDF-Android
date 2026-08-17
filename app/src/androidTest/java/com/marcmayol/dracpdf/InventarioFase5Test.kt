package com.marcmayol.dracpdf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFormularios
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.ui.inicio.PantallaInicio
import com.marcmayol.dracpdf.ui.inicio.TAG_MENU_ACERCA
import com.marcmayol.dracpdf.ui.inicio.TAG_MENU_AJUSTES
import com.marcmayol.dracpdf.ui.inicio.TAG_MENU_AYUDA
import com.marcmayol.dracpdf.ui.inicio.TAG_MENU_INICIO
import com.marcmayol.dracpdf.ui.inicio.TAG_MENU_TEMA
import com.marcmayol.dracpdf.ui.tema.HojaTema
import com.marcmayol.dracpdf.ui.tema.PreferenciaTema
import com.marcmayol.dracpdf.ui.tema.TAG_HOJA_TEMA
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import com.marcmayol.dracpdf.ui.tema.tagTema
import com.marcmayol.dracpdf.ui.visor.CachePaginas
import com.marcmayol.dracpdf.ui.visor.PantallaVisor
import com.marcmayol.dracpdf.ui.visor.TAG_ATRAS
import com.marcmayol.dracpdf.ui.visor.TAG_BARRA_MODO
import com.marcmayol.dracpdf.ui.visor.TAG_DESTINO_FORMULARIO
import com.marcmayol.dracpdf.ui.visor.TAG_MENU
import com.marcmayol.dracpdf.ui.visor.TAG_MENU_ABRIR
import com.marcmayol.dracpdf.ui.visor.TAG_MENU_COMPARTIR
import com.marcmayol.dracpdf.ui.visor.TAG_MENU_COPIA
import com.marcmayol.dracpdf.ui.visor.TAG_MENU_DOCUMENTOS
import com.marcmayol.dracpdf.ui.visor.TAG_MENU_IMPRIMIR
import com.marcmayol.dracpdf.ui.visor.TAG_MENU_PROPIEDADES
import com.marcmayol.dracpdf.ui.visor.TAG_MODO_ACCION
import com.marcmayol.dracpdf.ui.visor.TAG_MODO_CERRAR
import com.marcmayol.dracpdf.ui.visor.TAG_MODO_TITULO
import com.marcmayol.dracpdf.ui.visor.TAG_TITULO
import com.marcmayol.dracpdf.ui.visor.VisorViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Inventario de la Fase 5: lo que la conformidad visual añade a la interfaz.
 *
 * Dos reglas del diseño que hasta ahora no tenían quien las vigilara: que el tema se
 * elige y se queda elegido, y que **nunca hay dos barras superiores** —la de un modo
 * sustituye a la del documento, no se le suma—.
 */
@RunWith(AndroidJUnit4::class)
class InventarioFase5Test {
    @get:Rule
    val composicion = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val grafos = mutableListOf<Grafo>()

    @After
    fun cerrarDocumentos() {
        grafos.forEach { it.alTerminar() }
        grafos.clear()
    }

    // --------------------------------------------------- el menú y la hoja de tema

    @Test
    fun el_menu_del_inicio_ofrece_el_tema() {
        composicion.setContent { TemaDracPDF { PantallaInicio(alAbrirPdf = {}) } }
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_MENU_INICIO).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_MENU_INICIO).assertIsEnabled()
        composicion.onNodeWithTag(TAG_MENU_INICIO).performClick()
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_MENU_TEMA).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_MENU_TEMA).assertIsEnabled()
        // Las cuatro de la maqueta están; lo que aún no funciona se ve y no se pulsa.
        composicion.onNodeWithTag(TAG_MENU_AJUSTES).assertIsNotEnabled()
        composicion.onNodeWithTag(TAG_MENU_AYUDA).assertIsNotEnabled()
        composicion.onNodeWithTag(TAG_MENU_ACERCA).assertIsNotEnabled()
    }

    @Test
    fun la_hoja_de_tema_trae_las_tres_opciones_y_marca_la_puesta() {
        composicion.setContent {
            TemaDracPDF {
                HojaTema(elegida = PreferenciaTema.OSCURO, alElegir = {}, alCerrar = {})
            }
        }
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_HOJA_TEMA).assertIsDisplayed()
        PreferenciaTema.entries.forEach { preferencia ->
            composicion.onNodeWithTag(tagTema(preferencia)).assertIsDisplayed()
        }
    }

    @Test
    fun elegir_un_tema_lo_cambia_en_caliente_sin_recrear_la_pantalla() {
        var elegida by mutableStateOf(PreferenciaTema.OSCURO)
        composicion.setContent {
            TemaDracPDF(preferencia = elegida) {
                HojaTema(elegida = elegida, alElegir = { elegida = it }, alCerrar = {})
            }
        }
        composicion.waitForIdle()

        composicion.onNodeWithTag(tagTema(PreferenciaTema.CLARO)).performClick()
        composicion.waitForIdle()

        // La misma composición sigue en pie con el tema nuevo: nada se ha recreado, y
        // la hoja no se ha cerrado debajo del dedo.
        assertEquals(PreferenciaTema.CLARO, elegida)
        composicion.onNodeWithTag(TAG_HOJA_TEMA).assertIsDisplayed()
        composicion.onNodeWithTag(tagTema(PreferenciaTema.CLARO)).assertIsDisplayed()
    }

    // ------------------------------------------------------------ el ⋮ del visor

    @Test
    fun el_menu_del_visor_guarda_las_acciones_del_documento() {
        val modelo = visorConFormulario("inv5_menu.pdf")
        composicion.setContent { TemaDracPDF { PantallaVisor(modelo = modelo, alSalir = {}) } }
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_MENU).performClick()
        composicion.waitForIdle()

        // Lo que ya existe se pulsa…
        composicion.onNodeWithTag(TAG_MENU_DOCUMENTOS).assertIsEnabled()
        composicion.onNodeWithTag(TAG_MENU_ABRIR).assertIsEnabled()
        // …incluidas las propiedades, que las encendió la Fase 7. Este inventario vigila
        // que el menú siga entero, no que siga siendo el de la Fase 5.
        composicion.onNodeWithTag(TAG_MENU_PROPIEDADES).assertIsEnabled()
        // …y lo que aquí no se le ha dado a la pantalla se ve apagado, no escondido: un
        // menú que cambia de largo entre versiones obliga a buscarlo todo otra vez.
        composicion.onNodeWithTag(TAG_MENU_COPIA).assertIsNotEnabled()
        composicion.onNodeWithTag(TAG_MENU_IMPRIMIR).assertIsNotEnabled()
        composicion.onNodeWithTag(TAG_MENU_COMPARTIR).assertIsNotEnabled()
    }

    // ------------------------------------------- una barra superior, y sólo una

    @Test
    fun el_modo_formulario_sustituye_la_barra_del_documento() {
        val modelo = visorConFormulario("inv5_barra.pdf")
        composicion.setContent { TemaDracPDF { PantallaVisor(modelo = modelo, alSalir = {}) } }
        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.hayFormulario }

        // Fuera del modo manda la barra del documento.
        composicion.onNodeWithTag(TAG_ATRAS).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_BARRA_MODO).assertDoesNotExist()

        composicion.onNodeWithTag(TAG_DESTINO_FORMULARIO).performClick()
        composicion.waitForIdle()

        // Y dentro, la del modo **en su lugar**: si convivieran, «atrás» y «✕»
        // quedarían a un centímetro la una de la otra cerrando cosas distintas.
        composicion.onNodeWithTag(TAG_BARRA_MODO).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_MODO_TITULO).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_MODO_CERRAR).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_ATRAS).assertDoesNotExist()
        composicion.onNodeWithTag(TAG_TITULO).assertDoesNotExist()
    }

    @Test
    fun guardar_vive_en_la_barra_del_modo_y_espera_a_que_haya_cambios() {
        val modelo = visorConFormulario("inv5_guardar.pdf")
        composicion.setContent { TemaDracPDF { PantallaVisor(modelo = modelo, alSalir = {}) } }
        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.hayFormulario }
        composicion.onNodeWithTag(TAG_DESTINO_FORMULARIO).performClick()
        composicion.waitForIdle()

        // Recién entrado no hay nada que guardar: se ve apagado, no escondido.
        composicion.onNodeWithTag(TAG_MODO_ACCION).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_MODO_ACCION).assertIsNotEnabled()
    }

    @Test
    fun al_cerrar_el_modo_vuelve_la_barra_del_documento() {
        val modelo = visorConFormulario("inv5_volver.pdf")
        composicion.setContent { TemaDracPDF { PantallaVisor(modelo = modelo, alSalir = {}) } }
        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.hayFormulario }
        composicion.onNodeWithTag(TAG_DESTINO_FORMULARIO).performClick()
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_MODO_CERRAR).performClick()
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_BARRA_MODO).assertDoesNotExist()
        composicion.onNodeWithTag(TAG_ATRAS).assertIsDisplayed()
    }

    private fun visorConFormulario(nombre: String): VisorViewModel {
        val fichero = GeneradorFormularios.formulario(File(contexto.cacheDir, nombre))
        val grafo = Grafo(contexto).also(grafos::add)
        grafo.abrirDocumento(OrigenDocumento.Privado(fichero.absolutePath, nombre))
        val estado = grafo.registro.abiertos().first()
        return VisorViewModel(grafo.casosDelVisor, grafo.registro, CachePaginas(PRESUPUESTO_PRUEBA))
            .also { it.mostrar(estado.id) }
    }

    private companion object {
        const val PRESUPUESTO_PRUEBA = 32 * 1024 * 1024
        const val ESPERA_MS = 10_000L
    }
}
