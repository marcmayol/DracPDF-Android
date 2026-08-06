package com.marcmayol.dracpdf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFormularios
import com.marcmayol.dracpdf.dominio.modelo.IdCampo
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.ui.tema.PreferenciaTema
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import com.marcmayol.dracpdf.ui.visor.CachePaginas
import com.marcmayol.dracpdf.ui.visor.PantallaVisor
import com.marcmayol.dracpdf.ui.visor.TAG_DESTINO_FORMULARIO
import com.marcmayol.dracpdf.ui.visor.VisorViewModel
import com.marcmayol.dracpdf.ui.visor.tagCampo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * El documento pertenece al papel, no al tema.
 *
 * Un campo de formulario, un resaltado o una selección se guardan **dentro del PDF** y
 * tienen que verse igual en cualquier otro visor; que la aplicación esté en oscuro no
 * puede teñirlos. Aquí se comprueba con los píxeles de verdad y no con la constante:
 * comparar `ColoresPapel` consigo mismo pasaría aunque la pantalla pintara otra cosa.
 */
@RunWith(AndroidJUnit4::class)
class PapelInvarianteTest {
    @get:Rule
    val composicion = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val grafos = mutableListOf<Grafo>()

    @After
    fun cerrarDocumentos() {
        grafos.forEach { it.alTerminar() }
        grafos.clear()
    }

    /**
     * El tema se cambia **en caliente y sobre la misma composición**, que además es
     * como lo cambia el usuario desde la hoja de Tema. Montar dos composiciones no
     * valdría: la regla de Compose sólo admite un `setContent` por test, y con dos
     * documentos distintos se estarían comparando dos rasterizados en vez de dos temas.
     */
    @Test
    fun el_campo_de_formulario_se_ve_igual_en_los_dos_temas() {
        var preferencia by mutableStateOf(PreferenciaTema.OSCURO)
        val modelo = visorConFormulario("papel_invariante.pdf")

        composicion.setContent {
            TemaDracPDF(preferencia = preferencia) {
                PantallaVisor(modelo = modelo, alSalir = {})
            }
        }
        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.hayFormulario }
        composicion.onNodeWithTag(TAG_DESTINO_FORMULARIO).performClick()
        composicion.waitUntil(ESPERA_MS) { modelo.campos.value.containsKey(0) }
        // Con la página ya rasterizada: el overlay es translúcido y se mezcla con lo
        // que tenga debajo, así que sin esperar al bitmap se compararía un fondo de
        // papel contra un fondo de documento y no dos temas.
        composicion.waitUntil(ESPERA_MS) { modelo.paginas.value.isNotEmpty() }
        composicion.waitForIdle()

        val enOscuro = pixelesDelCampo()

        preferencia = PreferenciaTema.CLARO
        composicion.waitForIdle()
        val enClaro = pixelesDelCampo()

        assertEquals("El overlay cambió de tamaño entre temas", enOscuro.size, enClaro.size)
        val distintos = enOscuro.indices.count { enOscuro[it] != enClaro[it] }
        assertEquals("$distintos píxeles del overlay cambian con el tema", 0, distintos)
    }

    private fun pixelesDelCampo(): List<Int> {
        val mapa = composicion.onNodeWithTag(tagCampo(IdCampo(0, 0))).captureToImage().toPixelMap()
        return (0 until mapa.height).flatMap { y -> (0 until mapa.width).map { x -> mapa[x, y].value.toInt() } }
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
