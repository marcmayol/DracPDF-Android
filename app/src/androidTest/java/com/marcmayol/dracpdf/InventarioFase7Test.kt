package com.marcmayol.dracpdf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.ui.inicio.PantallaInicio
import com.marcmayol.dracpdf.ui.inicio.RecienteEnLista
import com.marcmayol.dracpdf.ui.inicio.TAG_SECCION_RECIENTES
import com.marcmayol.dracpdf.ui.inicio.tagOlvidarReciente
import com.marcmayol.dracpdf.ui.inicio.tagReciente
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import com.marcmayol.dracpdf.ui.visor.CachePaginas
import com.marcmayol.dracpdf.ui.visor.PantallaVisor
import com.marcmayol.dracpdf.ui.visor.TAG_BARRA_BUSQUEDA
import com.marcmayol.dracpdf.ui.visor.TAG_BUSCAR
import com.marcmayol.dracpdf.ui.visor.TAG_BUSQUEDA_ANTERIOR
import com.marcmayol.dracpdf.ui.visor.TAG_BUSQUEDA_CAMPO
import com.marcmayol.dracpdf.ui.visor.TAG_BUSQUEDA_CERRAR
import com.marcmayol.dracpdf.ui.visor.TAG_BUSQUEDA_CONTADOR
import com.marcmayol.dracpdf.ui.visor.TAG_BUSQUEDA_SIGUIENTE
import com.marcmayol.dracpdf.ui.visor.TAG_CAMPO_IR_A_PAGINA
import com.marcmayol.dracpdf.ui.visor.TAG_DESTINO_INDICE
import com.marcmayol.dracpdf.ui.visor.TAG_HOJA_INDICE
import com.marcmayol.dracpdf.ui.visor.TAG_MENU
import com.marcmayol.dracpdf.ui.visor.TAG_MENU_COMPARTIR
import com.marcmayol.dracpdf.ui.visor.TAG_MENU_COPIA
import com.marcmayol.dracpdf.ui.visor.TAG_MENU_IMPRIMIR
import com.marcmayol.dracpdf.ui.visor.TAG_MENU_PROPIEDADES
import com.marcmayol.dracpdf.ui.visor.TAG_TAB_INDICE
import com.marcmayol.dracpdf.ui.visor.TAG_TAB_MINIATURAS
import com.marcmayol.dracpdf.ui.visor.VisorViewModel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Inventario de la Fase 7: los fundamentos de visor móvil.
 *
 * Cada acción que la fase promete tiene que existir en la interfaz real y estar en su
 * estado correcto. Lo que aún no llega —«Guardar una copia», que es de la Fase 11— se
 * queda apagado y visible, como en el resto de la aplicación.
 */
@RunWith(AndroidJUnit4::class)
class InventarioFase7Test {
    @get:Rule
    val composicion = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val grafos = mutableListOf<Grafo>()

    @After
    fun cerrarDocumentos() {
        grafos.forEach { it.alTerminar() }
        grafos.clear()
    }

    @Test
    fun la_lupa_ya_se_puede_pulsar_y_abre_la_barra_de_buscar() {
        montarVisor("buscar.pdf")

        // Estuvo apagada desde la Fase 1 esperando a esta.
        composicion.onNodeWithTag(TAG_BUSCAR).assertIsEnabled()
        composicion.onNodeWithTag(TAG_BUSCAR).performClick()
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_BARRA_BUSQUEDA).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_BUSQUEDA_CAMPO).assertIsDisplayed()
        // Sin nada escrito no hay a dónde saltar: las flechas lo dicen.
        composicion.onNodeWithTag(TAG_BUSQUEDA_ANTERIOR).assertIsNotEnabled()
        composicion.onNodeWithTag(TAG_BUSQUEDA_SIGUIENTE).assertIsNotEnabled()
    }

    @Test
    fun buscar_algo_que_esta_enciende_el_contador_y_la_navegacion() {
        montarVisor("contador.pdf", paginas = 4)

        composicion.onNodeWithTag(TAG_BUSCAR).performClick()
        composicion.onNodeWithTag(TAG_BUSQUEDA_CAMPO).performTextInput("Pagina")
        // La búsqueda va por su cuenta y publica lo que encuentra según lo encuentra:
        // hay que esperar a que aparezca el contador, no dar por hecho que ya está.
        composicion.waitUntil(ESPERA_BUSQUEDA) {
            composicion.onAllNodesWithTag(TAG_BUSQUEDA_CONTADOR).fetchSemanticsNodes().isNotEmpty()
        }

        composicion.onNodeWithTag(TAG_BUSQUEDA_CONTADOR).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_BUSQUEDA_SIGUIENTE).assertIsEnabled()
        composicion.onNodeWithTag(TAG_BUSQUEDA_ANTERIOR).assertIsEnabled()
    }

    @Test
    fun cerrar_la_busqueda_devuelve_la_barra_del_documento() {
        montarVisor("cerrar-busqueda.pdf")

        composicion.onNodeWithTag(TAG_BUSCAR).performClick()
        composicion.waitForIdle()
        composicion.onNodeWithTag(TAG_BUSQUEDA_CERRAR).performClick()
        composicion.waitForIdle()

        // Nunca dos barras superiores: al salir de buscar vuelve la del documento.
        composicion.onNodeWithTag(TAG_BUSCAR).assertIsDisplayed()
    }

    @Test
    fun el_indice_del_documento_ya_no_es_una_pestana_apagada() {
        montarVisor("con-indice.pdf", conIndice = true)

        composicion.onNodeWithTag(TAG_DESTINO_INDICE).performClick()
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_HOJA_INDICE).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_TAB_MINIATURAS).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_TAB_INDICE).assertIsDisplayed()
        // Y «ir a la página», que es la otra forma de moverse por un documento largo.
        composicion.onNodeWithTag(TAG_CAMPO_IR_A_PAGINA).assertIsDisplayed()
    }

    @Test
    fun el_menu_del_visor_ya_imprime_comparte_y_ensena_las_propiedades() {
        montarVisor("menu.pdf")

        composicion.onNodeWithTag(TAG_MENU).performClick()
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_MENU_IMPRIMIR).assertIsEnabled()
        composicion.onNodeWithTag(TAG_MENU_COMPARTIR).assertIsEnabled()
        composicion.onNodeWithTag(TAG_MENU_PROPIEDADES).assertIsEnabled()
        // «Guardar una copia» es de la Fase 11: sigue viéndose y sin poder pulsarse.
        composicion.onNodeWithTag(TAG_MENU_COPIA).assertIsNotEnabled()
    }

    @Test
    fun el_inicio_ensena_los_recientes_y_deja_olvidarlos() {
        var olvidado: String? = null
        composicion.setContent {
            TemaDracPDF {
                PantallaInicio(
                    alAbrirPdf = {},
                    recientes =
                        listOf(
                            RecienteEnLista("uri://uno", "contrato.pdf", "hace 2 h", "pág. 4"),
                            RecienteEnLista("uri://dos", "manual.pdf", "hace 3 días", null, puedeQueNoAbra = true),
                        ),
                    alOlvidarReciente = { olvidado = it },
                )
            }
        }
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_SECCION_RECIENTES).assertIsDisplayed()
        composicion.onNodeWithTag(tagReciente("uri://uno")).assertIsDisplayed()
        composicion.onNodeWithTag(tagOlvidarReciente("uri://dos")).performClick()

        assert(olvidado == "uri://dos") { "Olvidar un reciente tiene que decir cuál" }
    }

    private fun montarVisor(
        nombre: String,
        paginas: Int = 3,
        conIndice: Boolean = false,
    ) {
        val fichero =
            if (conIndice) {
                GeneradorFixtures.conIndiceYEnlaces(File(contexto.cacheDir, nombre))
            } else {
                GeneradorFixtures.documento(File(contexto.cacheDir, nombre), paginas = paginas)
            }
        val grafo = Grafo(contexto).also(grafos::add)
        grafo.abrirDocumento(OrigenDocumento.Privado(fichero.absolutePath, nombre))
        val estado = grafo.registro.abiertos().first()
        val modelo =
            VisorViewModel(grafo.casosDelVisor, grafo.registro, CachePaginas(PRESUPUESTO_PRUEBA))
                .also { it.mostrar(estado.id) }

        composicion.setContent {
            TemaDracPDF { PantallaVisor(modelo = modelo, alSalir = {}, alImprimir = {}, alCompartirDocumento = {}) }
        }
        composicion.waitForIdle()
    }

    private companion object {
        const val PRESUPUESTO_PRUEBA = 32 * 1024 * 1024
        const val ESPERA_BUSQUEDA = 5_000L
    }
}
